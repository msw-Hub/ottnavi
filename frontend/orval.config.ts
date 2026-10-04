import { defineConfig } from 'orval'

// openapi.yaml(유일한 API 계약)에서 타입·TanStack Query 훅·MSW 목업을 생성한다. 실행: npm run api:generate
// 생성물(src/api/generated/)은 직접 수정하지 않는다. 재생성하면 덮어써진다.
export default defineConfig({
  ottnavi: {
    input: {
      // 초안 파일을 따로 두지 않고 계약 하나만 입력으로 쓴다. x-planned 표시가 붙은 operation도 생성 대상이다
      target: '../docs/api/openapi.yaml',
    },
    output: {
      mode: 'tags-split', // 태그(catalog, user 등) 단위로 파일을 나눈다
      target: 'src/api/generated',
      schemas: 'src/api/generated/model',
      client: 'react-query',
      httpClient: 'axios', // 미지정 시 fetch로 생성되어 custom mutator(axios)와 맞지 않는다
      clean: true, // 계약에서 사라진 operation의 파일이 남지 않게 한다
      mock: {
        generators: [
          {
            type: 'msw',
            useExamples: true, // 계약의 example 값을 목업 응답으로 쓴다
            baseUrl: '/api',
          },
        ],
      },
      override: {
        mutator: {
          path: './src/api/http.ts',
          name: 'customInstance',
        },
      },
    },
  },
})
