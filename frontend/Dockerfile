# Stage 1: Build - Node đầy đủ để chạy Vite build, không nằm trong image chạy cuối cùng
FROM node:20-alpine AS build
WORKDIR /app

# Cache dependency layer riêng - chỉ cài lại khi package*.json đổi, không phải mỗi lần sửa code
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Runtime - chỉ Nginx Alpine phục vụ static build, không có Node/source
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
# Outside conf.d on purpose: everything in conf.d is loaded at http level, this file is included per location.
COPY security-headers.conf /etc/nginx/security-headers.conf

EXPOSE 80
