#!/bin/sh

# Give vault some time to start up
sleep 2

# Enable KV v2 secrets engine at 'secret' path
vault secrets enable -path=secret kv-v2
