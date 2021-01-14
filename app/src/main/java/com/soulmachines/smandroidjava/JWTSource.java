package com.soulmachines.smandroidjava;

import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public interface JWTSource {

    void getJwt(OnSuccess success, OnError error);

    interface OnSuccess {
        void jwtToken(String jwtToken);
    }
    interface OnError {
        void error(String errorMessage);
    }


    /** Use a pre-generated JWT token. */
    class PregeneratedJwtSource implements JWTSource {

        private final String token;
        public PregeneratedJwtSource(String token) {
            this.token = token;
        }

        @Override
        public void getJwt(OnSuccess success, OnError error) {
            if(token != null && token.trim().length() > 0) {
                success.jwtToken(token);
            } else {
                error.error("The specified JWT token (jwt1_connection_access_token) cannot be empty.");
            }
        }
    }

    /** Using a self signed JWT token. */
    class SelfSigned implements JWTSource {
        private String privateKey;
        private String keyName;
        private String orchestrationServerUrl;

        public SelfSigned(String privateKey, String keyName, String orchestrationServerUrl) {
            this.privateKey = privateKey;
            this.keyName = keyName;
            this.orchestrationServerUrl = orchestrationServerUrl;
        }

        @Override
        public void getJwt(OnSuccess success, OnError error) {
            if(privateKey != null && privateKey.trim().length() > 0 && keyName != null && keyName.trim().length() > 0) {
                success.jwtToken(generateJwt(privateKey, keyName));
            } else {
                error.error("The specified private key (jwt3_ddna_studio_key_name) and key name (jwt3_ddna_studio_key_name) properties cannot be empty.");
            }
        }

        private String generateJwt(String privateKey, String keyName) {
            Key key  = Keys.hmacShaKeyFor(privateKey.getBytes());
            Date currentTime = Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant());
            JwtBuilder jwsBuilder = Jwts.builder()
                    .setHeaderParam("typ", "JWT")
                    .setNotBefore(currentTime)
                    .setIssuedAt(currentTime)
                    .setExpiration(Date.from(LocalDateTime.now().plusDays(1).atZone(ZoneId.systemDefault()).toInstant()))
                    .claim("iss", keyName);

            if(orchestrationServerUrl != null && orchestrationServerUrl.trim().length() > 0) {
                // add extra details about the orchestration server
                jwsBuilder
                        .claim("sm-control-via-browser", true)
                        .claim("sm-control", orchestrationServerUrl);
            }
            return jwsBuilder.signWith(key).compact();
        }

    }




}

