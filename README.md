<p align="center">
  <img src="logo.svg" />
</p>

# Secure Drop

Secure Drop is a collection of tools to securely extract files from your Android phone, end-to-end encrypted until they reach your trusted device.

 * Android app ([prebuilt APK](https://github.com/pb-/secure-drop/raw/master/binaries/securedrop.apk), or [source](android))
 * Backend ([source](backend))
 * Client ([prebuilt](https://github.com/pb-/secure-drop/raw/master/binaries/sdc) or [source](client))
   * Requires a Java 11 JRE and `openssl` on your system

Secure Drop is self hosted. You will need to set up a backend yourself to use it.


## Installation

It makes sense to set up the components in order.


### Backend

A [Dockerfile](backend/Dockerfile) is provided. You'll need to specify a volume for `/data` and set `UPLOAD_TOKEN` as well as `DOWNLOAD_TOKEN`. Both tokens can be identical; use separate ones for additional security - the Android app never reads data.

Example `docker-compose.yml`:

```yaml
services:
  securedrop:
    image: ...
    volumes:
      - /data/securedrop:/data
    environment:
      - UPLOAD_TOKEN=(use pwgen -s 32 1)
      - DOWNLOAD_TOKEN=(use pwgen -s 32 1)
```


### Client

Compile it yourself or use [this prebuilt binary](https://github.com/pb-/secure-drop/raw/master/binaries/sdc). Run the client without arguments to configure it interactively. It should generate a private key for you and you can set the backend API endpoint (e.g. `https://backend/api`) and the `DOWNLOAD_TOKEN`.

If everything is set up correctly and the backend is running, you should be able to run `sdc batch list` without any errors (and probably empty output). Run `sdc` without argument for usage help.


### Android app

Compile it yourself or use a [prebuilt APK](https://github.com/pb-/secure-drop/raw/master/binaries/securedrop.apk). Configure a drop zone using the `UPLOAD_TOKEN` for the backend, its endpoint, and your client's public key. You can get the public key by running

```
openssl rsa -in ~/.config/secure-drop/private-key -inform DER -pubout -outform DER | \
  base64 -w 0 > /tmp/public-key.txt
```
