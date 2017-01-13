package org.sb.mailrelay;


import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;

class CredHelper
{
    private static final String SCOPE_GMAIL = "https://mail.google.com/";
    private static final Logger log = Logger.getLogger(CredHelper.class.getPackage().getName());
    private static final String redirURI = "urn:ietf:wg:oauth:2.0:oob";
    private final Path userToken;
    final HttpTransport httpTransport;
    static final JsonFactory jfac = JacksonFactory.getDefaultInstance();
    private final GoogleClientSecrets clientSecrets;
    
    CredHelper(Path mailrelay, HttpTransport httpTransport) throws IOException
    {
        if(Files.notExists(mailrelay)) throw new FileNotFoundException("The directory "  + mailrelay + " does not exist.");
        this.httpTransport = httpTransport;
        userToken = mailrelay.resolve("usertoken");
        Path clientSecretsFile = mailrelay.resolve("client_secrets.json");
        while(Files.notExists(clientSecretsFile))
        {
            log.fine("Could not find " + clientSecretsFile.toAbsolutePath());
            System.out.println("Please type the absolute path of the 'client_secrets.json' file:");
            Path src = Paths.get(readLine());
            if(Files.exists(src) && !Files.isDirectory(src))
            {
                log.info("Copying " + src + " to " + clientSecretsFile);
                Files.copy(src, clientSecretsFile, StandardCopyOption.REPLACE_EXISTING);
            }
            else
                log.info("Path " + src + " is not valid.");
        }
        clientSecrets = GoogleClientSecrets.load(jfac, new FileReader(clientSecretsFile.toFile()));
    }

    Credential authorize(String userID) throws IOException
    {
        GoogleAuthorizationCodeFlow flow = getFlow(true);
        System.out.println("Please open the following URL in your browser, login, accept and then type the authorization code shown on the page:");
        System.out.println(" " + flow.newAuthorizationUrl().setRedirectUri(redirURI).build());
        String code = readLine();
        GoogleTokenResponse response =
                new GoogleAuthorizationCodeTokenRequest(httpTransport, jfac,
                        clientSecrets.getDetails().getClientId(), clientSecrets.getDetails().getClientSecret(),
                        code, redirURI).execute();
        log.fine(() -> "Got token response:" + response);
        return flow.createAndStoreCredential(response, userID);
    }

    private GoogleAuthorizationCodeFlow getFlow(boolean force) throws IOException
    {
        return new GoogleAuthorizationCodeFlow.Builder(httpTransport, jfac, 
                                                                clientSecrets, Collections.singleton(SCOPE_GMAIL))
                        .setAccessType("offline")
                        .setApprovalPrompt(force ? "force" : "auto")
                        .setDataStoreFactory(new FileDataStoreFactory(userToken.toFile()))
                        .build();
    }
    
    Optional<Credential> get(String userID) throws IOException
    {
        GoogleAuthorizationCodeFlow flow = getFlow(false);
        return Optional.ofNullable(flow.loadCredential(userID));
    }

    Optional<Credential> reauthorize(String userID) throws IOException
    {
        Supplier<String> resp = Try.uncheck(() -> { 
            System.out.println("Please confirm that you want to authenticate again (y/n):");
            return readLine();
        });
        
        if(Files.notExists(userToken) || resp.get().startsWith("y")) return Optional.of(authorize(userID));
        else 
        {
            System.out.println("Continuing with existing credentials ...");
            return get(userID);
        }
    }
    
    private static String readLine() throws IOException
    {
        String code = System.console().readLine();
        return code != null ? code.trim() : "";
    }

    static CredHelper makeCredHelper(Path home) throws IOException, IllegalStateException
    {
        Path mailrelay = home.resolve(".mailrelay");
        
        if(Files.notExists(mailrelay)) Files.createDirectory(mailrelay);
        else if(!Files.isDirectory(mailrelay))
            throw new IllegalStateException("The path '" + mailrelay.getFileName() 
                    + "' already exists in '" + mailrelay.getParent() + "', but is not a directory.");
        
        try
        {
            final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            return new CredHelper(mailrelay, httpTransport);
        }
        catch (GeneralSecurityException e)
        {
            throw new RuntimeException(e);
        }
    }
}
