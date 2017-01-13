package org.sb.mailrelay;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Login implements Cmd
{
	private static final Logger log = Logger.getLogger(Login.class.getPackage().getName());
	
    public void exec(final Path home, List<String> opts) throws IOException, IllegalStateException
    {
		Map<String, String> nvp = Cmd.nvpFlags(opts.stream());
		String senderAddress = nvp.get("email");
		if(senderAddress == null) throw new IllegalStateException("No 'email' option provided");
        CredHelper credHelper = CredHelper.makeCredHelper(home);
        
        Path userToken = home.resolve("usertoken");
        if(Files.exists(userToken))
    		credHelper.reauthorize(senderAddress);
        else
        	credHelper.authorize(senderAddress);
    }
    
    @Override
	public List<String> help(String name) 
	{
		return Stream.concat(
					Stream.of("login --email=<GMail-address>"),
				    Stream.of(
					"Login requires authentication using your favourite browser",
					"Example: login --email=abc@gmail.com")
				    	 .map(s -> " \t " + s))
				.collect(Collectors.toList());
				
	}    
}
