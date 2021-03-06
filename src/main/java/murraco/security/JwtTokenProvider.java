package murraco.security;

import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import jdk.nashorn.internal.runtime.logging.Logger;
import lombok.extern.slf4j.Slf4j;
import murraco.model.User;
import murraco.repository.UserRepository;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.joda.time.ReadablePeriod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import murraco.exception.CustomException;
import murraco.model.Role;

@Slf4j
@Component
public class JwtTokenProvider {

  /**
   * THIS IS NOT A SECURE PRACTICE! For simplicity, we are storing a static key here. Ideally, in a
   * microservices environment, this key would be kept on a config-server.
   */
  @Value("${security.jwt.token.secret-key:secret-key}")
  private String secretKey;

  @Value("${security.jwt.token.expire-length:3600000}")
  private long validityInMilliseconds = 36; // 1h

  @Autowired
  private MyUserDetails myUserDetails;

  @Autowired
  private UserRepository userRepository;

  @PostConstruct
  protected void init() {
    secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
  }

  public String createToken(String username, List<Role> roles) {
    String token;
    Claims claims = Jwts.claims().setSubject(username);
    claims.put("auth", roles.stream().map(s -> new SimpleGrantedAuthority(s.getAuthority())).filter(Objects::nonNull).collect(Collectors.toList()));



    Date now = new Date();
    Date validity = new Date(now.getTime() + validityInMilliseconds);


    token = Jwts.builder()//
        .setClaims(claims)//
        .setIssuedAt(now)//
        //.setExpiration(validity)//
        .signWith(SignatureAlgorithm.HS256, secretKey)//
        .compact();
    //update token with user
    User user = userRepository.findByUsername(username);
    log.info(now + " now and repotime " + user.getLastAccessed());
    user.setLastAccessed(now);
    user.setLastToken(token);
    user.setDateGenerated(now);
    userRepository.save(user);


    return token;
  }

  public Authentication getAuthentication(String token) {
    UserDetails userDetails = myUserDetails.loadUserByUsername(getUsername(token));
    return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
  }

  public String getUsername(String token) {
    return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getSubject();
  }

  public String getID(String token){
    return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().getId();
  }

  public String resolveToken(HttpServletRequest req) {
    String bearerToken = req.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7, bearerToken.length());
    }
    return null;
  }

  public boolean validateToken(String token) {
    Date storedLastAccessed;
    Date now = new Date();
    String username;
    try {
      log.info("hihihihihihihihi");
      //log.info(Jwts.parser().setSigningKey(secretKey).parseClaimsJwt(token).getBody().toString());
      //Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
      //storedLastAccessed=Jwts.parser().setSigningKey(secretKey).parseClaimsJwt(token).getBody();
      //username =Jwts.parser().setSigningKey(secretKey).parseClaimsJwt(token).getBody().getSubject();
      username = "admin";
      //Object objects = claims.get("");
      //when create token add token to the db linked to user
      //compare lasttoken with current token, if same proceed, if not return false

      System.out.println("userRepository :- " + userRepository);
      System.out.println("token :- " + token);

      //log.info(userRepository.findByUsername(username).getLastToken().toString());

      if (userRepository.findByUsername(username).getLastToken() != null ) {
        if(userRepository.findByUsername(username).getLastToken().equals(token)) {
          storedLastAccessed = userRepository.findByUsername(username).getLastAccessed();
          Long timeDiff = now.getTime() - storedLastAccessed.getTime();
          //log.info(String.valueOf(now.getTime()-storedLastAccessed.getTime()));

          if (timeDiff > 100 * 1000) {   //Milli-S
            log.info("Should Expire");
            return false;
          } else {
            log.info("Should extend lastAccessed");
            //update lastAccessedDate
            User user = userRepository.findByUsername(username);
            log.info(now + " now and repotime " + user.getLastAccessed());
            user.setLastAccessed(now);
            userRepository.save(user);
            return true;
          }
        }else{
          log.info("token existing in database");
          log.info(String.valueOf(userRepository.findByUsername(username).getDateGenerated().compareTo(now)));
          if(userRepository.findByUsername(username).getDateGenerated().compareTo(now)>0){
            log.info("update token and last accessed");
            User user = userRepository.findByUsername(username);
            user.setLastToken(token);
            user.setLastAccessed(now);
            userRepository.save(user);
            return true;
          }else{
            log.info("fail if token is not newer than last accessed");
            return false;
          }
        }
      } else {
        log.info("Initialize token and lastAccessed" + userRepository.findByUsername(username));
        User user = userRepository.findByUsername(username);
        user.setLastToken(token);
        user.setLastAccessed(now);
        userRepository.save(user);
        return true;
      }
      //    } catch (JwtException | IllegalArgumentException e) {
    } catch (IllegalArgumentException e) {
      throw new CustomException("Expired or invalid JWT token", HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

}
