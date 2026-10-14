package com.ottnavi.global.error;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 오류를 ProblemDetail(application/problem+json) + errorCode로 응답한다.
 * Spring MVC 내장 예외는 부모 클래스가 처리한 뒤 handleExceptionInternal 한 곳으로 모이므로, 거기서 errorCode와 한국어 detail을 붙인다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	public static final String ERROR_CODE = "errorCode";     // ProblemDetail 확장 필드명
	public static final String FIELD_ERRORS = "fieldErrors"; // VALIDATION_FAILED 필드 오류 목록 필드명

	private static final String TYPE_MISMATCH_MESSAGE = "형식이 올바르지 않습니다.";
	private static final String MISSING_PARAMETER_MESSAGE = "필수 값입니다.";

	// 도메인 예외

	/** 도메인 예외를 ErrorCode의 상태와 예외 메시지로 응답한다 */
	@ExceptionHandler(BusinessException.class)
	public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest request) {
		ErrorCode errorCode = ex.getErrorCode();
		String detail = errorCode.getMessage();
		if (ex.getMessage() != null) {
			detail = ex.getMessage();
		}
		logByStatus(errorCode.getStatus(), errorCode, request.getRequestURI(), ex);
		return problemDetail(errorCode.getStatus(), errorCode, detail);
	}

	/** 필드를 지정한 검증 실패를 400 VALIDATION_FAILED와 fieldErrors 한 건으로 응답한다 */
	@ExceptionHandler(FieldValidationException.class)
	public ProblemDetail handleFieldValidationException(FieldValidationException ex, HttpServletRequest request) {
		ErrorCode errorCode = ex.getErrorCode();
		ProblemDetail problem = problemDetail(errorCode.getStatus(), errorCode, errorCode.getMessage());
		List<FieldErrorDetail> fieldErrors = new ArrayList<>();
		fieldErrors.add(new FieldErrorDetail(ex.getField(), ex.getMessage()));
		problem.setProperty(FIELD_ERRORS, fieldErrors);
		logByStatus(errorCode.getStatus(), errorCode, request.getRequestURI(), ex);
		return problem;
	}

	// 인증·인가 예외 (보안 필터의 엔트리포인트·거부 핸들러도 HandlerExceptionResolver를 거쳐 여기로 온다)

	/** 인증 실패를 401 UNAUTHORIZED로 응답한다 */
	@ExceptionHandler(AuthenticationException.class)
	public ProblemDetail handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
		return simpleProblem(ErrorCode.UNAUTHORIZED, request, ex);
	}

	/** 권한 부족을 403 FORBIDDEN으로 응답한다 */
	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
		return simpleProblem(ErrorCode.FORBIDDEN, request, ex);
	}

	// 처리되지 않은 예외

	/** 예상하지 못한 예외를 500 INTERNAL_ERROR로 응답한다. 내부 메시지는 응답에 노출하지 않는다 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedException(Exception ex, HttpServletRequest request) {
		return simpleProblem(ErrorCode.INTERNAL_ERROR, request, ex);
	}

	// Spring MVC 내장 예외 공통 처리

	/** 내장 예외의 응답 본문을 errorCode·한국어 detail을 붙인 ProblemDetail로 바꾼다 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
			Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
		ErrorCode errorCode = resolveErrorCode(ex, statusCode);
		// 상태는 내장 예외가 정한 값을 유지한다(예: 415, 406은 INVALID_REQUEST지만 상태는 그대로)
		ProblemDetail problem = problemDetail(statusCode, errorCode, errorCode.getMessage());
		if (errorCode == ErrorCode.VALIDATION_FAILED) {
			problem.setProperty(FIELD_ERRORS, extractFieldErrors(ex));
		}
		logByStatus(statusCode, errorCode, requestPath(request), ex);
		return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
	}

	/** 내장 예외 유형과 상태로 errorCode를 정한다 */
	private ErrorCode resolveErrorCode(Exception ex, HttpStatusCode statusCode) {
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
			if (ex instanceof MethodArgumentNotValidException
					|| ex instanceof HandlerMethodValidationException
					|| ex instanceof TypeMismatchException
					|| ex instanceof MissingServletRequestParameterException) {
				return ErrorCode.VALIDATION_FAILED;
			}
			// 본문 값의 형식·타입 불일치(날짜 형식, 모르는 enum 값 등)는 필드를 특정할 수 있어 VALIDATION_FAILED
			if (ex instanceof HttpMessageNotReadableException && findMismatchedInput(ex) != null) {
				return ErrorCode.VALIDATION_FAILED;
			}
			// 깨진 JSON(HttpMessageNotReadableException) 등 필드를 특정할 수 없는 400.
			// detail은 ErrorCode 문구로 덮어쓰므로 Jackson 내부 메시지가 응답에 섞이지 않는다
			return ErrorCode.INVALID_REQUEST;
		}
		if (ex instanceof HttpRequestMethodNotSupportedException) {
			return ErrorCode.METHOD_NOT_ALLOWED;
		}
		return switch (statusCode.value()) {
			case 401 -> ErrorCode.UNAUTHORIZED;
			case 403 -> ErrorCode.FORBIDDEN;
			case 404 -> ErrorCode.RESOURCE_NOT_FOUND; // NoResourceFoundException, NoHandlerFoundException
			case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
			case 429 -> ErrorCode.RATE_LIMIT_EXCEEDED;
			default -> statusCode.is4xxClientError() ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
		};
	}

	/** VALIDATION_FAILED 대상 예외에서 필드별 오류 목록을 뽑는다 */
	private List<FieldErrorDetail> extractFieldErrors(Exception ex) {
		List<FieldErrorDetail> fieldErrors = new ArrayList<>();
		if (ex instanceof MethodArgumentNotValidException notValid) {
			addBindingErrors(notValid.getBindingResult(), fieldErrors);
		} else if (ex instanceof HandlerMethodValidationException methodValidation) {
			for (ParameterValidationResult result : methodValidation.getParameterValidationResults()) {
				if (result instanceof ParameterErrors parameterErrors) {
					addBindingErrors(parameterErrors, fieldErrors); // @Valid 객체 파라미터는 필드 단위로
				} else {
					String parameterName = result.getMethodParameter().getParameterName();
					for (MessageSourceResolvable error : result.getResolvableErrors()) {
						fieldErrors.add(new FieldErrorDetail(parameterName, error.getDefaultMessage()));
					}
				}
			}
		} else if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
			fieldErrors.add(new FieldErrorDetail(mismatch.getName(), TYPE_MISMATCH_MESSAGE));
		} else if (ex instanceof TypeMismatchException mismatch) {
			fieldErrors.add(new FieldErrorDetail(mismatch.getPropertyName(), TYPE_MISMATCH_MESSAGE));
		} else if (ex instanceof MissingServletRequestParameterException missing) {
			fieldErrors.add(new FieldErrorDetail(missing.getParameterName(), MISSING_PARAMETER_MESSAGE));
		} else if (ex instanceof HttpMessageNotReadableException) {
			// 본문 값 형식 불일치: 경로(a.b[0])를 필드명으로 쓴다
			MismatchedInputException mismatch = findMismatchedInput(ex);
			if (mismatch != null) {
				fieldErrors.add(new FieldErrorDetail(buildJsonPath(mismatch), TYPE_MISMATCH_MESSAGE));
			}
		}
		return fieldErrors;
	}

	/** 검증 결과(BindingResult·ParameterErrors)의 필드 오류와 객체 오류(필드명은 객체명)를 목록에 더한다 */
	private void addBindingErrors(Errors errors, List<FieldErrorDetail> fieldErrors) {
		for (ObjectError error : errors.getAllErrors()) {
			String field = error.getObjectName();
			if (error instanceof FieldError fieldError) {
				field = fieldError.getField();
			}
			fieldErrors.add(new FieldErrorDetail(field, error.getDefaultMessage()));
		}
	}

	/**
	 * 원인 사슬에서 경로가 비어 있지 않은 Jackson MismatchedInputException(InvalidFormatException 포함)을 찾는다.
	 * 경로가 비면(본문 전체가 객체가 아닌 경우 등) 필드를 특정할 수 없어 null을 반환한다.
	 */
	private MismatchedInputException findMismatchedInput(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
				return mismatch;
			}
			if (current.getCause() == current) {
				return null;
			}
			current = current.getCause();
		}
		return null;
	}

	/** Jackson 경로를 a.b[0] 형태 문자열로 만든다. 필드명은 점으로 잇고 배열 위치는 [n]으로 붙인다 */
	private String buildJsonPath(JacksonException ex) {
		StringBuilder path = new StringBuilder();
		for (JacksonException.Reference reference : ex.getPath()) {
			if (reference.getPropertyName() != null) {
				if (!path.isEmpty()) {
					path.append('.');
				}
				path.append(reference.getPropertyName());
			} else if (reference.getIndex() >= 0) {
				path.append('[').append(reference.getIndex()).append(']');
			}
		}
		return path.toString();
	}

	/** 고정 문구로 응답하는 예외를 ProblemDetail로 만든다 */
	private ProblemDetail simpleProblem(ErrorCode errorCode, HttpServletRequest request, Exception ex) {
		logByStatus(errorCode.getStatus(), errorCode, request.getRequestURI(), ex);
		return problemDetail(errorCode.getStatus(), errorCode, errorCode.getMessage());
	}

	private ProblemDetail problemDetail(HttpStatusCode status, ErrorCode errorCode, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty(ERROR_CODE, errorCode.name());
		return problem;
	}

	/** 4xx는 예상 가능한 실패라 warn(스택 없음), 5xx는 error(스택 포함)로 남긴다 */
	private void logByStatus(HttpStatusCode status, ErrorCode errorCode, String path, Exception ex) {
		if (status.is5xxServerError()) {
			log.error("요청 처리 중 서버 오류: errorCode={}, status={}, path={}", errorCode, status.value(), path, ex);
		} else {
			log.warn("요청 처리 실패: errorCode={}, status={}, path={}, exception={}",
					errorCode, status.value(), path, ex.getClass().getSimpleName());
		}
	}

	private String requestPath(WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest) {
			return servletRequest.getRequest().getRequestURI();
		}
		return request.getDescription(false);
	}
}
