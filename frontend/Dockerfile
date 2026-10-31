FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
EXPOSE 5176
CMD ["sh", "-c", "[ -d node_modules/@rollup/rollup-linux-x64-musl ] || npm install; npx vite --host 0.0.0.0 --port ${FRONTEND_PORT:-5176}"]


