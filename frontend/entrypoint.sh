#!/bin/sh
BACKEND_HOST=${BACKEND_HOST:-backend}
BACKEND_PORT=${BACKEND_PORT:-8080}
SOCKETIO_PORT=${SOCKETIO_PORT:-8089}
export BACKEND_HOST BACKEND_PORT SOCKETIO_PORT

envsubst '${BACKEND_HOST} ${BACKEND_PORT} ${SOCKETIO_PORT}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

exec nginx -g 'daemon off;'
