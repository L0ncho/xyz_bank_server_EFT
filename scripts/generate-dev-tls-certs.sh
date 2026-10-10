#!/usr/bin/env bash
# Generates one throwaway development certificate authority and, signed by it:
# a TLS server certificate for bff-web, bff-mobile, bff-atm, and core-service's
# PIN-verification-only connector, plus the ATM terminal's mTLS client certificate.
#
# Dev/test use only. Never use these certificates or this CA in a real deployment.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CERTS_DIR="${ROOT_DIR}/dev/certs"
PASS="xyzbank-dev"
VALIDITY_DAYS=3650

rm -rf "${CERTS_DIR}"
mkdir -p "${CERTS_DIR}"

echo "== Generating dev CA =="
keytool -genkeypair \
  -alias ca \
  -keyalg RSA -keysize 2048 -validity "${VALIDITY_DAYS}" \
  -keystore "${CERTS_DIR}/ca.p12" -storetype PKCS12 -storepass "${PASS}" -keypass "${PASS}" \
  -dname "CN=XYZ Bank Dev CA,OU=dev,O=xyzbank" \
  -ext bc:c=ca:true \
  -ext ku:c=keyCertSign,cRLSign

keytool -exportcert \
  -alias ca -keystore "${CERTS_DIR}/ca.p12" -storepass "${PASS}" \
  -rfc -file "${CERTS_DIR}/ca.crt"

echo "== Building shared dev truststore (CA cert only) =="
keytool -importcert -noprompt \
  -alias ca -file "${CERTS_DIR}/ca.crt" \
  -keystore "${CERTS_DIR}/truststore.p12" -storetype PKCS12 -storepass "${PASS}"

issue_leaf_cert() {
  local name="$1" alias="$2" dname="$3" san="$4" is_client="$5"
  local dir="${CERTS_DIR}/${name}"
  mkdir -p "${dir}"

  local ext_usage
  if [ "${is_client}" = "true" ]; then
    ext_usage="-ext ku:c=digitalSignature -ext eku:c=clientAuth"
  else
    ext_usage="-ext ku:c=digitalSignature,keyEncipherment -ext eku:c=serverAuth"
  fi

  echo "== Generating keypair for ${name} =="
  keytool -genkeypair \
    -alias "${alias}" \
    -keyalg RSA -keysize 2048 -validity "${VALIDITY_DAYS}" \
    -keystore "${dir}/keystore.p12" -storetype PKCS12 -storepass "${PASS}" -keypass "${PASS}" \
    -dname "${dname}" \
    ${ext_usage} ${san:+-ext "san=${san}"}

  keytool -certreq \
    -alias "${alias}" -keystore "${dir}/keystore.p12" -storepass "${PASS}" \
    -file "${dir}/${name}.csr" \
    ${san:+-ext "san=${san}"}

  echo "== Signing ${name}'s certificate with the dev CA =="
  keytool -gencert \
    -alias ca -keystore "${CERTS_DIR}/ca.p12" -storepass "${PASS}" \
    -infile "${dir}/${name}.csr" -outfile "${dir}/${name}.crt" \
    -validity "${VALIDITY_DAYS}" -rfc \
    ${ext_usage} ${san:+-ext "san=${san}"}

  keytool -importcert -noprompt \
    -alias ca -file "${CERTS_DIR}/ca.crt" \
    -keystore "${dir}/keystore.p12" -storepass "${PASS}"
  keytool -importcert -noprompt \
    -alias "${alias}" -file "${dir}/${name}.crt" \
    -keystore "${dir}/keystore.p12" -storepass "${PASS}"

  rm -f "${dir}/${name}.csr"
}

issue_leaf_cert "bff-web" "bff-web" \
  "CN=bff-web,OU=dev,O=xyzbank" \
  "dns:bff-web,dns:localhost,ip:127.0.0.1" \
  false

issue_leaf_cert "bff-mobile" "bff-mobile" \
  "CN=bff-mobile,OU=dev,O=xyzbank" \
  "dns:bff-mobile,dns:localhost,ip:127.0.0.1" \
  false

issue_leaf_cert "bff-atm" "bff-atm" \
  "CN=bff-atm,OU=dev,O=xyzbank" \
  "dns:bff-atm,dns:localhost,ip:127.0.0.1" \
  false

issue_leaf_cert "core-service" "core-service" \
  "CN=core-service,OU=dev,O=xyzbank" \
  "dns:core-service,dns:localhost,ip:127.0.0.1" \
  false

issue_leaf_cert "atm-terminal" "atm-terminal-001" \
  "CN=atm-terminal-001,OU=dev,O=xyzbank" \
  "" \
  true

echo "== Verifying every leaf certificate chains to the dev CA =="
for name in bff-web bff-mobile bff-atm core-service atm-terminal; do
  echo "-- ${name} --"
  keytool -list -v -keystore "${CERTS_DIR}/${name}/keystore.p12" -storepass "${PASS}" \
    | grep -E "Owner:|Issuer:|Alias name:"
done

echo "== Copying keystores/truststore into each module's test resources =="
copy_module_tls() {
  local module_dir="$1" leaf_name="$2"
  local target="${ROOT_DIR}/${module_dir}/src/test/resources/tls"
  mkdir -p "${target}"
  cp "${CERTS_DIR}/${leaf_name}/keystore.p12" "${target}/keystore.p12"
  cp "${CERTS_DIR}/truststore.p12" "${target}/truststore.p12"
}

copy_module_tls "bff/bff-web" "bff-web"
copy_module_tls "bff/bff-mobile" "bff-mobile"
copy_module_tls "bff/bff-atm" "bff-atm"
cp "${CERTS_DIR}/atm-terminal/keystore.p12" "${ROOT_DIR}/bff/bff-atm/src/test/resources/tls/terminal-keystore.p12"
copy_module_tls "platform/core-service" "core-service"
mkdir -p "${ROOT_DIR}/platform/gateway/src/test/resources/tls"
cp "${CERTS_DIR}/ca.crt" "${ROOT_DIR}/platform/gateway/src/test/resources/tls/ca.crt"
cp "${CERTS_DIR}/bff-web/keystore.p12" "${ROOT_DIR}/platform/gateway/src/test/resources/tls/keystore.p12"

echo "== Done. Dev CA and certificates are under ${CERTS_DIR} (dev/test use only). =="
