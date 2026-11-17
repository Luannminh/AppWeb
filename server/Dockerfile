# TwoChat server — small image, JSON storage on a volume.
FROM node:22-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY src ./src
COPY public ./public

ENV PORT=3000 \
    HOST=0.0.0.0 \
    DATA_FILE=/data/twochat.json

VOLUME /data
EXPOSE 3000

CMD ["node", "src/index.js"]
