FROM ubuntu:24.04

# The Ubuntu base image can end up with stale or HTTP package metadata sources,
# which causes 404s when `apt-get` resolves packages from the Ubuntu mirror.
# Normalize to HTTPS and refresh the release metadata before installing tools.
RUN export DEBIAN_FRONTEND=noninteractive \
    && sed -i 's#http://archive.ubuntu.com/ubuntu#https://archive.ubuntu.com/ubuntu#g; s#http://security.ubuntu.com/ubuntu#https://security.ubuntu.com/ubuntu#g' /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || true \
    && apt-get update --allow-releaseinfo-change \
    && apt-get install -y --no-install-recommends ca-certificates curl bash git \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="/root/.local/bin:${PATH}"

RUN curl -sSfL https://raw.githubusercontent.com/MystenLabs/suiup/main/install.sh | sh \
    && suiup install sui@testnet

WORKDIR /workspace

ENTRYPOINT ["sui"]
