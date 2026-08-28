FROM node:22-bookworm-slim AS cloud-builder
WORKDIR /app/webApp
ARG VITE_ANDROID_PACKAGE_NAME=com.alicia.cloudstorage.phone
ENV VITE_ANDROID_PACKAGE_NAME=$VITE_ANDROID_PACKAGE_NAME

COPY webApp/package.json webApp/package-lock.json ./
RUN npm ci

COPY webApp/index.html webApp/tsconfig.json webApp/tsconfig.app.json webApp/tsconfig.node.json webApp/vite.config.ts ./
COPY webApp/public public
COPY webApp/scripts scripts
COPY webApp/src src

RUN npm run build

FROM node:22-bookworm-slim AS cloud-console-builder
WORKDIR /app/sysManage

COPY sysManage/package.json sysManage/package-lock.json ./
RUN npm ci

COPY sysManage/index.html sysManage/tsconfig.json sysManage/tsconfig.app.json sysManage/tsconfig.node.json sysManage/vite.config.ts ./
COPY sysManage/public public
COPY sysManage/scripts scripts
COPY sysManage/src src

RUN npm run build

FROM nginx:1.27-alpine
COPY webApp/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=cloud-builder /app/webApp/dist /usr/share/nginx/html/cloudPan
COPY --from=cloud-console-builder /app/sysManage/dist /usr/share/nginx/html/console/cloud

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
