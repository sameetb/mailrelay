/**
 * 
 */
package org.sb.mailrelay;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Message.RecipientType;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.sun.mail.smtp.SMTPTransport;

/**
 * @author sam
 *
 */
public class Send implements Cmd 
{
	private static final Logger log = Logger.getLogger(Send.class.getPackage().getName());
	
	private Map<String, Credential> credMap = new ConcurrentHashMap<String, Credential>();
	private Map<String, ConnMgr<SMTPTransport>> transMap = new ConcurrentHashMap<>();
	
	/* (non-Javadoc)
	 * @see org.sb.mailrelay.Cmd#exec(java.util.List)
	 */
	@Override
	public void exec(Path home, List<String> opts) throws IOException, IllegalStateException 
	{
        try {
			Map<String, String> nvp = Cmd.nvpFlags(opts.stream());
			Set<String> flags = Cmd.booleanFlags(opts.stream());
			
			boolean cache = flags.contains("cache");
			
			String senderAddress = nvp.get("from");
			if(senderAddress == null) throw new IllegalStateException("No 'from' option specified");

			Message msg = makeMessage(nvp, flags);
			Address[] allRecipients = msg.getAllRecipients();
			if(allRecipients == null || allRecipients.length == 0) throw new IllegalStateException("No recipients specified");
			
			log.fine(() -> "Sending message " + msg + "to " + Arrays.toString(allRecipients));
			
			Credential cred = getCreds(home, senderAddress, cache);
				
			SMTPTransport smtpTransport = getTransport(nvp, flags, senderAddress, cred, cache);
			
			smtpTransport.sendMessage(msg, allRecipients);
			if(!cache) smtpTransport.close();
		} catch (MessagingException | GeneralSecurityException e) {
			// TODO Auto-generated catch block
			throw new IOException(e);
		}

	}

	protected SMTPTransport getTransport(Map<String, String> nvp, Set<String> flags, String senderAddress,
			Credential cred, boolean cache) throws IOException 
	{
		Supplier<SMTPTransport> ts = Try.uncheck(() -> OauthProvider.connectToSmtp(nvp.getOrDefault("serverAddress", "smtp.gmail.com"),
				Integer.parseInt(nvp.getOrDefault("serverPort", "587")),
				senderAddress,
		        cred.getAccessToken(),
		        flags.contains("debug")));
		
		if(cache)
		{
			ConnMgr<SMTPTransport> st = transMap.get(senderAddress);
			if(st == null )
			synchronized(this)
			{
				st = transMap.get(senderAddress);
				if(st == null )
				{
					st = ConnMgr.wrap(ts, t -> t.isConnected(), Try.uncheck(t -> t.close()));
					transMap.put(senderAddress, st);
				}
			}
			return st.get();
		}
		return ts.get();
	}

	protected Credential getCreds(Path home, String senderAddress, boolean cache) throws GeneralSecurityException, IOException 
	{
		Credential cred = null;
		Supplier<Credential> scred = Try.uncheck(() -> makeCred(home, senderAddress));
		if(cache)
		{
			cred = credMap.get(senderAddress);
			if(cred == null)
			synchronized(this)
			{
				cred = credMap.get(senderAddress);
				if(cred == null)
				{
					cred = scred.get();
					credMap.put(senderAddress, cred);
				}
			}
		}
		else
			cred = scred.get();
		
		if(isExpired(cred))
		synchronized(cred)	
		{
			log.info("Refreshing expired token");
			cred.refreshToken();
		}
		
		return cred;
	}

	private Credential makeCred(Path home, String senderAddress) throws GeneralSecurityException, IOException {
		HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		CredHelper credHelper = new CredHelper(mailrelay(home), httpTransport);
		Credential cred = credHelper.get(senderAddress)
		                    .orElseThrow(() -> new IOException("Did not find credentials for " 
		                    						+ senderAddress + "from " + mailrelay(home)));
		return cred;
	}

	private boolean isExpired(Credential cred)
	{
		Long exp = cred.getExpiresInSeconds();
		log.fine(() -> "Token expiry is " + exp);
		return exp == null || exp < 60;
	}
	
	private Path mailrelay(Path home)
    {
        return home.resolve(".mailrelay");
    }	
    
    protected Message makeMessage(Map<String, String> nvp, Set<String> flags) throws MessagingException, IOException
    {
    	MimeMessage msg = new MimeMessage((Session)null);
    	Optional<File> attachment = Optional.ofNullable(nvp.get("file")).map(f -> new File(f));
    	
    	String text = flags.contains("stdin") ? readSysIn() : nvp.getOrDefault("text", "No text provided");
    	
		if(!attachment.isPresent())
    		msg.setContent(text, "text/plain");
    	else
    	{
    		File file = attachment.get();
    		log.fine(() -> "Attachment specifed " + file +  ", sending multipart message");
			if(!file.exists() || !file.canRead() || file.isDirectory())
    			throw new IllegalStateException("The file specified " + file + "cannot be  attached");
    		MimeMultipart multipart = new MimeMultipart();
    		MimeBodyPart messageBodyPart = new MimeBodyPart();
    		messageBodyPart.setText(text);
    		multipart.addBodyPart(messageBodyPart);
    		
    		messageBodyPart = new MimeBodyPart();
    		messageBodyPart.setDataHandler(new DataHandler(new FileDataSource(file)));
    		messageBodyPart.setFileName(file.getName());
    		multipart.addBodyPart(messageBodyPart);
    	
    		msg.setContent(multipart);
    		
    	}
    	msg.setSentDate(new Date());
    	msg.setSubject(nvp.getOrDefault("subject", "No subject"));
		Optional.ofNullable(nvp.get("to")).ifPresent(Try.uncheck(to -> msg.setRecipients(RecipientType.TO, to)));
		Optional.ofNullable(nvp.get("cc")).ifPresent(Try.uncheck(to -> msg.setRecipients(RecipientType.CC, to)));
		Optional.ofNullable(nvp.get("bcc")).ifPresent(Try.uncheck(to -> msg.setRecipients(RecipientType.BCC, to)));
    	return msg;
    }

	@Override
	public List<String> help(String name) 
	{
		return Stream.concat(
					Stream.of("send \t sends email using GMail over SMTP with following options:"),
				    Stream.of(
					"--from : the sender's GMail adddress, the same should already have logged in",
					"--to : comma separated TO recipients",
					"--serverAddress : smtp server addres (opt)",
					"--serverPort : smptp server port (opt)",
					"--cc : comma separated CC recipients (opt)",
					"--bcc : comma separated BCC recipients (opt)",
					"--text : text of the message (opt)",
					"--stdin : pipe the text to be sent from standard input (opt)",
					"--subject : subject of the message (opt)",
					"--file : file attachment, must exist and be readable (opt)",
					"--debug : enable smtp debugging",
					"at least one recipient must be specified",
					"Example: send --from=abc@gmail.com --to=xyz@gmail.com --text=\"What's up yo\"")
				    	 .map(s -> " \t " + s))
				.collect(Collectors.toList());
				
	}
	
	private String readSysIn() throws IOException
	{
		log.fine(() -> "Reading message body from standard input");

		System.out.println("please type in your message: (Ctrl-D to finish)");
		
		StringBuffer sb = new StringBuffer();
		char[] buff = new char[2048];  
		InputStreamReader br = new InputStreamReader(System.in);
		
		int ret = 0;
		while((ret = br.read(buff)) != -1) sb.append(buff, 0, ret);
		return sb.toString();
	}
}
