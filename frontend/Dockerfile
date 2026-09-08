# 실행 전용 이미지. 빌드는 CI(npm run build)에서 끝내고 dist 만 담는다.
# RUN 단계가 없어 buildx 로 arm64 이미지를 에뮬레이션 없이 만들 수 있다.
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
