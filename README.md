<p align="center">
  <img src="logo.svg" />
</p>

# Secure Drop

Extract files from your Android phone, encrypted with a public key.


## Key-material generation for end-to-end encryption

Create a 4096-bit RSA private key:

```shell
mkdir ~/.config/secure-drop
openssl genrsa 4096 | \
  openssl pkcs8 -topk8 -outform DER -nocrypt -out ~/.config/secure-drop/private-key
```

Extract the public part from the private key as base64:

```shell
openssl rsa -in ~/.config/secure-drop/private-key -inform DER -pubout -outform DER | \
  base64 -w 0 > /tmp/public-key.txt
```

Copy the contents of `/tmp/public-key.txt` into the Android app.
