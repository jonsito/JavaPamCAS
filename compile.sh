#!/bin/bash

cacerts_file=""
[ -f /etc/pki/java/cacerts ] && cacerts_file=/etc/pki/java/cacerts # Fedora
[ -f /etc/ssl/java/cacerts ] && cacerts_file=/etc/ssl/java/cacerts # Ubuntu

fs=""
mkdir -p out
mkdir -p logs
touch logs/trace.log
case "Z${1}" in
  "Z-c" | "Z--compile" )
      javac -cp ./jars/org.eclipse.swt.gtk.linux.x86_64_3.111.0.v20190605-1801.jar:./jars/org.eclipse.swt_3.111.0.v20190605-1801.jar:./src \
          -d out \
          ./src/es/upm/dit/upm_authenticator/UPMAuthenticator.java
      ;;
  "Z-r" | "Z--run" )
      [ "Z${2}" = "Z-f" ] && fs="fullscreen"
      java -cp ./jars/org.eclipse.swt.gtk.linux.x86_64_3.111.0.v20190605-1801.jar:./jars/org.eclipse.swt_3.111.0.v20190605-1801.jar:./out \
                es/upm/dit/upm_authenticator/UPMAuthenticator ${fs}
      ;;
  "Z-k" | "Z--key" | "Z--keytool" )
      if [ "Z${cacerts_file}" = "Z" ]; then
        echo "cannot locate CACerts file. Abort"
        exit 1
      fi
      sudo keytool -importcert \
          -alias acceso.lab.dit.upm.es \
          -file ./certs/acceso-lab-dit-upm-es-chain.pem \
          -keystore ${cacerts_file}
      ;;
  * )
      echo "Usage ${0} <option>"
      echo "Options:"
      echo "-c || --compile     to compile"
      echo "-r || --run         to execute ( add -f to run in full screen mode) "
      echo "-k || --key         to install certificate in keystore"
      ;;
esac
