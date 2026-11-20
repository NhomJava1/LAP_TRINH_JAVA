FROM node:22-alpine
WORKDIR /app
COPY signer/package.json signer/package-lock.json ./
RUN npm ci
COPY signer/tsconfig.json ./
COPY signer/src ./src
RUN npm run build
EXPOSE 3001
CMD ["npm", "start"]
