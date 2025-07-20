# `server` module

## Overview
- It should provide a server for the card games.
  - It is a module that:
    - Provides an online multiplayer experience.
    - Handles game logic and state.
    - Manages player connections.
    - Supports multiple game sessions.
    - Can manage the console and GUI clients.
    - Has to write down all the games locally using the `game-recorder` module.

## Set up

- Create a postgres database called `ultracards` and user for the server.
- Add the database _user_ and _user password_ to the `application.properties` file.
- Add the email and the [_app password_](https://support.google.com/accounts/answer/185833) to the `application.properties` file.
- Add a real JWT token. You can use this sample program for that
```java
import io.jsonwebtoken.security.MacAlgorithm;
import io.jsonwebtoken.security.SecretKeyFactory;
import io.jsonwebtoken.io.Encoders;

import javax.crypto.SecretKey;

public class JwtSecretGenerator {
  public static void main(String[] args) {
    
    var macAlg = MacAlgorithm.HS256;
    
    var key = macAlg.key().build();
    
    var base64Secret = Encoders.BASE64.encode(key.getEncoded());
    System.out.println("Base64 JWT secret: " + base64Secret);
  }
}
```
