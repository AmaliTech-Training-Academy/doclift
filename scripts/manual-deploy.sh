#!/usr/bin/env bash
# Manual fallback for .github/workflows/cd.yml — build/push/redeploy the doclift
# images by hand when CI can't run (e.g. the appleboy/ssh-action runtime
# download fails). Mirrors that workflow's steps 1:1.
#
# Usage:
#   scripts/manual-deploy.sh build            # build backend/frontend/caddy images locally
#   scripts/manual-deploy.sh push             # push them to ghcr.io
#   scripts/manual-deploy.sh deploy           # ssh to EC2, pull + docker compose up
#   scripts/manual-deploy.sh prune            # remove dangling images on the EC2 host
#   scripts/manual-deploy.sh all              # build + push + deploy + prune
#
# Required env vars (set in your shell or a local .env.deploy you `source` first):
#   GITHUB_TOKEN     - GHCR login token (needs write:packages for push, read:packages for pull)
#   GH_USERNAME      - your GitHub username, used for `docker login ghcr.io`
#   EC2_HOST         - EC2 host/IP for the deploy/prune steps
#   EC2_USER         - SSH user on the EC2 box
#   EC2_SSH_KEY_PATH - path to the private key file for SSH
#   EC2_PORT         - SSH port (default 22)
#   OWNER            - image namespace on ghcr.io (default amalitech-training-academy)
#   TAG              - image tag to build/push/deploy (default: current git SHA)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGISTRY="ghcr.io"
OWNER="${OWNER:-amalitech-training-academy}"
TAG="${TAG:-$(git -C "$REPO_ROOT" rev-parse HEAD)}"
EC2_PORT="${EC2_PORT:-22}"

IMAGES=(backend frontend caddy)

image_ref() {
  echo "${REGISTRY}/${OWNER}/doclift-$1"
}

cmd_build() {
  echo "==> Building images (tag: ${TAG})"
  docker build -t "$(image_ref backend):${TAG}" -t "$(image_ref backend):latest" \
    "${REPO_ROOT}/apps/backend"

  docker build -t "$(image_ref frontend):${TAG}" -t "$(image_ref frontend):latest" \
    --build-arg BACKEND_URL=http://backend:8080 \
    "${REPO_ROOT}/apps/web"

  docker build -t "$(image_ref caddy):${TAG}" -t "$(image_ref caddy):latest" \
    "${REPO_ROOT}/caddy"
}

cmd_push() {
  : "${GITHUB_TOKEN:?set GITHUB_TOKEN}" "${GH_USERNAME:?set GH_USERNAME}"
  echo "==> Logging in to ${REGISTRY}"
  echo "${GITHUB_TOKEN}" | docker login "${REGISTRY}" -u "${GH_USERNAME}" --password-stdin

  echo "==> Pushing images (tag: ${TAG})"
  for name in "${IMAGES[@]}"; do
    docker push "$(image_ref "$name"):${TAG}"
    docker push "$(image_ref "$name"):latest"
  done
}

cmd_deploy() {
  : "${EC2_HOST:?set EC2_HOST}" "${EC2_USER:?set EC2_USER}" "${EC2_SSH_KEY_PATH:?set EC2_SSH_KEY_PATH}"
  : "${GITHUB_TOKEN:?set GITHUB_TOKEN}" "${GH_USERNAME:?set GH_USERNAME}"

  echo "==> Deploying tag ${TAG} to ${EC2_HOST}"
  ssh -i "${EC2_SSH_KEY_PATH}" -p "${EC2_PORT}" "${EC2_USER}@${EC2_HOST}" bash -s <<EOF
set -e
echo "${GITHUB_TOKEN}" | docker login ${REGISTRY} -u ${GH_USERNAME} --password-stdin
cd /opt/doclift
export BACKEND_IMAGE=$(image_ref backend):${TAG}
export FRONTEND_IMAGE=$(image_ref frontend):${TAG}
export CADDY_IMAGE=$(image_ref caddy):${TAG}
docker compose pull
docker compose up -d --remove-orphans --wait --wait-timeout 120
EOF
}

cmd_prune() {
  : "${EC2_HOST:?set EC2_HOST}" "${EC2_USER:?set EC2_USER}" "${EC2_SSH_KEY_PATH:?set EC2_SSH_KEY_PATH}"
  echo "==> Pruning dangling images on ${EC2_HOST}"
  ssh -i "${EC2_SSH_KEY_PATH}" -p "${EC2_PORT}" "${EC2_USER}@${EC2_HOST}" \
    'docker image prune -f'
}

case "${1:-}" in
  build)  cmd_build ;;
  push)   cmd_push ;;
  deploy) cmd_deploy ;;
  prune)  cmd_prune ;;
  all)    cmd_build; cmd_push; cmd_deploy; cmd_prune ;;
  *)
    echo "Usage: $0 {build|push|deploy|prune|all}" >&2
    exit 1
    ;;
esac
