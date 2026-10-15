package com.ottnavi.global.security.token;

import com.ottnavi.global.security.jwt.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis에 Refresh Token 계열을 저장하는 구현.
 * 키: rt:fam:{familyId} HASH(userId, currentHash, previousHash, rotatedAt, createdAt, TTL=refresh-ttl),
 *     rt:user:{userId} SET(familyId 목록). 기존 rl:public:·rl:fetch: 접두사와 겹치지 않는다.
 * 교체·폐기 판정은 Lua 스크립트 하나로 원자적으로 처리해 같은 토큰의 동시 요청이 경쟁하지 않게 한다.
 * 토큰 원문·familyId는 로그에 남기지 않는다.
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

	private static final String FAMILY_PREFIX = "rt:fam:";
	private static final String USER_PREFIX = "rt:user:";
	private static final int FAMILY_ID_BYTES = 16;   // 128비트
	private static final int SECRET_BYTES = 32;      // 256비트
	// 형식이 맞지 않는 값은 Redis에 닿기 전에 걸러낸다(키 주입 방지). 16바이트는 22자, 32바이트는 43자(base64url, 패딩 없음)
	private static final Pattern TOKEN_PATTERN = Pattern.compile("^([A-Za-z0-9_-]{22})\\.([A-Za-z0-9_-]{43})$");

	/** 새 계열 생성. KEYS[1]=계열 키, KEYS[2]=사용자 계열 목록. ARGV: userId, familyId, hash, ttlMs */
	private static final String ISSUE_SCRIPT = """
			local t = redis.call('TIME')
			local nowMs = t[1] * 1000 + math.floor(t[2] / 1000)
			redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'currentHash', ARGV[3], 'createdAt', nowMs)
			redis.call('PEXPIRE', KEYS[1], ARGV[4])
			redis.call('SADD', KEYS[2], ARGV[2])
			redis.call('PEXPIRE', KEYS[2], ARGV[4])
			return 1
			""";

	/**
	 * 교체 판정. KEYS[1]=계열 키. ARGV: 받은 해시, 새 해시, 유예(ms), TTL(ms).
	 * 반환: {결과, userId}. 현재 해시면 교체, 직전 해시가 유예 안이면 GRACE, 그 밖이면 재사용으로 보고 계열을 지운다.
	 */
	private static final String ROTATE_SCRIPT = """
			local vals = redis.call('HMGET', KEYS[1], 'userId', 'currentHash', 'previousHash', 'rotatedAt')
			if not vals[1] then
			  return {'NOT_FOUND', ''}
			end
			local t = redis.call('TIME')
			local nowMs = t[1] * 1000 + math.floor(t[2] / 1000)
			if ARGV[1] == vals[2] then
			  redis.call('HSET', KEYS[1], 'currentHash', ARGV[2], 'previousHash', vals[2], 'rotatedAt', nowMs)
			  redis.call('PEXPIRE', KEYS[1], ARGV[4])
			  return {'ROTATED', vals[1]}
			end
			if vals[3] and ARGV[1] == vals[3] then
			  local rotatedAt = tonumber(vals[4])
			  if rotatedAt and (nowMs - rotatedAt) <= tonumber(ARGV[3]) then
			    return {'GRACE', vals[1]}
			  end
			end
			redis.call('DEL', KEYS[1])
			return {'REUSED', vals[1]}
			""";

	/** 폐기. KEYS[1]=계열 키. ARGV: 받은 해시. 현재·직전 해시가 맞을 때만 지우고 userId를 돌려준다(아니면 빈 문자열) */
	private static final String REVOKE_SCRIPT = """
			local vals = redis.call('HMGET', KEYS[1], 'userId', 'currentHash', 'previousHash')
			if not vals[1] then
			  return ''
			end
			if ARGV[1] == vals[2] or (vals[3] and ARGV[1] == vals[3]) then
			  redis.call('DEL', KEYS[1])
			  return vals[1]
			end
			return ''
			""";

	@SuppressWarnings("rawtypes")
	private final DefaultRedisScript<List> rotateScript = new DefaultRedisScript<>(ROTATE_SCRIPT, List.class);
	private final DefaultRedisScript<String> revokeScript = new DefaultRedisScript<>(REVOKE_SCRIPT, String.class);
	private final DefaultRedisScript<Long> issueScript = new DefaultRedisScript<>(ISSUE_SCRIPT, Long.class);

	private final StringRedisTemplate redis;
	private final JwtProperties properties;
	private final SecureRandom random = new SecureRandom();

	public RedisRefreshTokenStore(StringRedisTemplate redis, JwtProperties properties) {
		this.redis = redis;
		this.properties = properties;
	}

	@Override
	public String issue(Long userId) {
		// 1. 계열 번호와 비밀 값을 난수로 만든다
		String familyId = randomUrlSafe(FAMILY_ID_BYTES);
		String secret = randomUrlSafe(SECRET_BYTES);
		// 2. 서버에는 비밀 값의 해시만 저장한다
		redis.execute(issueScript, List.of(FAMILY_PREFIX + familyId, USER_PREFIX + userId),
				String.valueOf(userId), familyId, hash(secret), String.valueOf(ttlMillis()));
		return familyId + "." + secret;
	}

	@Override
	@SuppressWarnings("unchecked")
	public RefreshRotation rotate(String rawToken) {
		// 1. 형식이 틀리면 Redis를 부르지 않고 없는 계열로 본다
		Matcher matcher = match(rawToken);
		if (matcher == null) {
			return RefreshRotation.notFound();
		}
		// 2. 새 비밀 값을 자바에서 만들고 해시만 스크립트에 넘긴다(원문은 응답용으로 자바가 보관)
		String familyId = matcher.group(1);
		String newSecret = randomUrlSafe(SECRET_BYTES);
		List<Object> reply = redis.execute(rotateScript, List.of(FAMILY_PREFIX + familyId),
				hash(matcher.group(2)), hash(newSecret),
				String.valueOf(properties.refreshReuseGrace().toMillis()), String.valueOf(ttlMillis()));
		// 3. 판정 결과를 값으로 바꾼다
		String status = String.valueOf(reply.get(0));
		if ("NOT_FOUND".equals(status)) {
			return RefreshRotation.notFound();
		}
		Long userId = Long.valueOf(String.valueOf(reply.get(1)));
		if ("ROTATED".equals(status)) {
			return RefreshRotation.rotated(userId, familyId + "." + newSecret);
		}
		if ("GRACE".equals(status)) {
			return RefreshRotation.grace(userId);
		}
		// 4. REUSED: 계열은 스크립트가 지웠고, 사용자 목록의 잔여 항목도 정리한다
		redis.opsForSet().remove(USER_PREFIX + userId, familyId);
		return RefreshRotation.reused(userId);
	}

	@Override
	public void revoke(String rawToken) {
		Matcher matcher = match(rawToken);
		if (matcher == null) {
			return;
		}
		String familyId = matcher.group(1);
		String userIdText = redis.execute(revokeScript, List.of(FAMILY_PREFIX + familyId), hash(matcher.group(2)));
		if (userIdText != null && !userIdText.isEmpty()) {
			redis.opsForSet().remove(USER_PREFIX + userIdText, familyId);
		}
	}

	@Override
	public void revokeAllOf(Long userId) {
		String userKey = USER_PREFIX + userId;
		Set<String> familyIds = redis.opsForSet().members(userKey);
		if (familyIds != null) {
			for (String familyId : familyIds) {
				redis.delete(FAMILY_PREFIX + familyId);
			}
		}
		redis.delete(userKey);
	}

	/** 토큰 형식이 맞으면 familyId(1)와 secret(2)을 담은 Matcher를, 아니면 null을 돌려준다 */
	private Matcher match(String rawToken) {
		if (rawToken == null) {
			return null;
		}
		Matcher matcher = TOKEN_PATTERN.matcher(rawToken);
		if (!matcher.matches()) {
			return null;
		}
		return matcher;
	}

	private long ttlMillis() {
		return properties.refreshTtl().toMillis();
	}

	private String randomUrlSafe(int bytes) {
		byte[] buffer = new byte[bytes];
		random.nextBytes(buffer);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
	}

	/** 비밀 값의 SHA-256을 base64url로 만든다(저장·비교용) */
	private String hash(String secret) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}
}
