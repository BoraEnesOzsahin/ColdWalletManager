#!/bin/sh

# Wait for Vault to become reachable
echo "Waiting for Vault to start at $VAULT_ADDR..."
while true; do
  vault status > /dev/null 2>&1
  RES=$?
  # vault status returns 0 if unsealed and initialized
  # vault status returns 2 if sealed or uninitialized
  # vault status returns 1 for other errors (like connection refused)
  if [ $RES -eq 0 ] || [ $RES -eq 2 ]; then
    break
  fi
  sleep 2
done
echo "Vault is reachable."

# Check if Vault is initialized
if vault status -format=json | grep -q '"initialized": false'; then
  echo "Vault is not initialized. Initializing..."

  # Initialize Vault and capture the output
  vault operator init -key-shares=1 -key-threshold=1 > /vault/file/init.out

  # Extract the Unseal Key and Root Token
  UNSEAL_KEY=$(grep 'Unseal Key 1:' /vault/file/init.out | awk '{print $NF}')
  ROOT_TOKEN=$(grep 'Initial Root Token:' /vault/file/init.out | awk '{print $NF}')

  # Save keys for future use
  echo $UNSEAL_KEY > /vault/file/unseal.key
  echo $ROOT_TOKEN > /vault/file/root.token

  # Unseal Vault
  vault operator unseal $UNSEAL_KEY

  # Login with root token to setup Vault
  vault login $ROOT_TOKEN
  export VAULT_TOKEN=$ROOT_TOKEN

  # 1. Enable the KV v2 engine at 'secret' path
  vault secrets enable -path=secret kv-v2

  # 2. Setup AppRole for the Spring Boot application
  vault auth enable approle

  # 3. Create a policy for the Spring Boot application
  cat <<EOF > /tmp/wallet-service-policy.hcl
path "secret/data/wallets/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "secret/metadata/wallets/*" {
  capabilities = ["list", "read", "delete"]
}
EOF
  vault policy write wallet-service-policy /tmp/wallet-service-policy.hcl

  # 4. Create an AppRole and attach the policy
  vault write auth/approle/role/wallet-service \
    secret_id_ttl=0 \
    token_num_uses=0 \
    token_ttl=1h \
    token_max_ttl=24h \
    secret_id_num_uses=0 \
    policies=wallet-service-policy

  # 5. Read the RoleID and generate a SecretID
  ROLE_ID=$(vault read -field=role_id auth/approle/role/wallet-service/role-id)
  SECRET_ID=$(vault write -f -field=secret_id auth/approle/role/wallet-service/secret-id)

  # Save them to a file so we can pass them to the app container
  echo $ROLE_ID > /vault/file/role.id
  echo $SECRET_ID > /vault/file/secret.id

  echo "Vault initialization complete."
else
  echo "Vault is already initialized."

  # If Vault is sealed, unseal it
  if vault status -format=json | grep -q '"sealed": true'; then
    echo "Vault is sealed. Unsealing..."
    UNSEAL_KEY=$(cat /vault/file/unseal.key)
    vault operator unseal $UNSEAL_KEY
    echo "Vault unsealed."
  else
    echo "Vault is already unsealed."
  fi
fi

# Exit gracefully
exit 0
