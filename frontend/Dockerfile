FROM node:20-alpine
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY . .
EXPOSE 5176
CMD ["sh", "-c", "npx vite --host 0.0.0.0 --port ${FRONTEND_PORT:-5176}"]
