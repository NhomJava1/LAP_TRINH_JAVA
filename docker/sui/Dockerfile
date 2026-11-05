FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl bash git \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/root/.local/bin:${PATH}"

RUN curl -sSfL https://raw.githubusercontent.com/MystenLabs/suiup/main/install.sh | sh \
    && suiup install sui@testnet

WORKDIR /workspace

ENTRYPOINT ["sui"]
