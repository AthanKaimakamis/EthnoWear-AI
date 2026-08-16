#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
seed_dir="${script_dir}/../demo-media-seed"
api_root="${ETHNOWEAR_API_ROOT:-http://localhost:8080}"
: "${ETHNOWEAR_ADMIN_USERNAME:?Set ETHNOWEAR_ADMIN_USERNAME}"
: "${ETHNOWEAR_ADMIN_PASSWORD:?Set ETHNOWEAR_ADMIN_PASSWORD}"

while IFS=$'\t' read -r file checksum license media_type category description source; do
  [[ -z "${file}" || "${file}" == \#* ]] && continue
  candidate="${seed_dir}/${file}"
  [[ -f "${candidate}" ]] || { echo "Missing seed file: ${file}" >&2; exit 1; }
  actual="$(shasum -a 256 "${candidate}" | awk '{print $1}')"
  [[ "${actual}" == "${checksum}" ]] || { echo "Checksum mismatch: ${file}" >&2; exit 1; }
  metadata="$(printf '{\"mediaType\":\"%s\",\"category\":\"%s\",\"description\":\"%s License: %s. Source: %s\"}' \
    "${media_type}" "${category}" "${description}" "${license}" "${source}")"
  curl --fail --silent --show-error --user "${ETHNOWEAR_ADMIN_USERNAME}:${ETHNOWEAR_ADMIN_PASSWORD}" \
    -F "file=@${candidate}" -F "metadata=${metadata};type=application/json" \
    "${api_root}/api/admin/media-assets/upload"
  echo
done < "${seed_dir}/manifest.tsv"
