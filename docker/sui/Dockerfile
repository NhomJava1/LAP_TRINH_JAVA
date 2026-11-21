FROM ubuntu:24.04

# Keep Ubuntu's GPG-verified HTTP mirror for initial bootstrap because minimal
# ubuntu:24.04 does not include ca-certificates yet.
RUN export DEBIAN_FRONTEND=noninteractive \
    && apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl bash git \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/root/.local/bin:${PATH}"

RUN curl -sSfL https://raw.githubusercontent.com/MystenLabs/suiup/main/install.sh | sh \
    && suiup install sui@testnet

WORKDIR /workspace

ENTRYPOINT ["sui"]
