package edu.kit.aifb.cognitiveapps;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

public class LinuxInteractor {
 
	public static int executeCommand(String mode, String[] command, boolean waitForResponse) {
		int exit = -10000;	
		String response = "";
		ProcessBuilder pb = null;		
		if(mode.equals("shell")) {
			pb = new ProcessBuilder("sh", command[0], command[1], command[2], command[3], command[4]);
			pb.redirectErrorStream(true);
		}
		else if(mode.equals("normal")) {
			pb = new ProcessBuilder("curl", "-iF", command[0], command[1]);
			
		}
		try {
			Process shell = pb.start();
			System.out.println("################ Started");
			if (waitForResponse) {
				InputStream shellIn = shell.getInputStream();
				System.out.println("################ Getting");
				int shellExitStatus = shell.waitFor();
				System.out.println("################ Waiting");
				response = response + convertStreamToStr(shellIn);
				shellIn.close();
				exit = shellExitStatus;
			}
		}
		catch (IOException e) {
			System.out.println("Error occured while executing Linux command. Error Description: "
					+ e.getMessage());
		}
		catch (InterruptedException e) {
			System.out.println("Error occured while executing Linux command. Error Description: "
					+ e.getMessage());
		}
		return exit;
	}
	
/*
* To convert the InputStream to String we use the Reader.read(char[]
* buffer) method. We iterate until the Reader return -1 which means
* there's no more data to read. We use the StringWriter class to
* produce the string.
*/
 
	public static String convertStreamToStr(InputStream is) throws IOException {
		if (is != null) {
			Writer writer = new StringWriter();
			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(is,
						"UTF-8"));
				int n;
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				is.close();
			}
			return writer.toString();
		}
		else {
			return "";
		}
	}																						
 
}
