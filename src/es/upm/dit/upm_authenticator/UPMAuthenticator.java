package es.upm.dit.upm_authenticator;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class UPMAuthenticator {

	private final String NET_URL = "https://acceso.lab.dit.upm.es/login/index.html";
	private final String AUTHLOCAL_URL = "https://acceso.lab.dit.upm.es/login/php/auth_local.php";
	private final String SIU_URL = "https://siupruebas.upm.es/cas/login?authCAS=CAS";
	private final String SUCCESS_URL = "https://acceso.lab.dit.upm.es/login/success.html";

	private final Display display = new Display();
	private final Shell shell;
	private Browser browser;

	private Hashtable<String,String> retrieveData(String s) throws IOException{
		String line;
		Hashtable<String,String> result= new Hashtable<String,String>();
		String key=null,value=null;
		BufferedReader reader=new BufferedReader(new StringReader(s));
		System.err.println("Recuperando datos...");
		while ( (line= reader.readLine()) !=null) {
			// System.err.println(line);
			// locate key/value pairs
			int from=line.indexOf("<td><kbd><span>");
			int to=line.indexOf("</span></kbd></td>");
			if ((from>=0) && (to>=0)) {
				// item key found. get it
				key=line.substring(from+15,to);
			}
			from=line.indexOf("<td><code><span>[");
			to=line.indexOf("]</span></code></td>");
			if ((from>=0) && (to>=0)) {
				// found value for previous key. convert from iso-latin1 to utf-8
				byte[] b=line.substring(from+17,to).getBytes(StandardCharsets.ISO_8859_1);
				value=new String(b);
			}
			if (key!=null && value!=null ) {
				result.put(key,new String(value));
				key=null; value=null;
			}
		}
		reader.close();
		return result;
	}

    public UPMAuthenticator(boolean fullScreen) {
        display.setRuntimeExceptionHandler(new Consumer<RuntimeException>() {
			@Override
			public void accept(RuntimeException t) {
				System.err.println("Se ha producido una excepción en la ejecución de la ventana "+t.getMessage());
				// t.printStackTrace();
			}
        });
        display.setErrorHandler(new Consumer<Error>() {
			@Override
			public void accept(Error t) {
				System.err.println("Se ha pproducido un error en la JVM "+t.getMessage());
			}
        });
        shell = new Shell(display);
        FormLayout formLayout = new FormLayout();
        shell.setLayout(formLayout);
		if (fullScreen) shell.setFullScreen(fullScreen);
		else shell.setSize(1440,900);

        try {
        	if (SWT.getVersion() > 5100) browser = new Browser(shell, 0x40000); // SWT.EDGE);
        	else        				browser = new Browser(shell, SWT.NONE);
            browser.addProgressListener(new ProgressListener() {
				@Override
				public void changed(ProgressEvent arg0) {
					// System.err.println("Estamos desacargado página web "+arg0.toString());
				}

				@Override
				public void completed(ProgressEvent arg0) {
					// System.err.println("Progress completed "+arg0.toString());
					browser.getParent().layout();
					String data=browser.getText();
					if (data.indexOf("Laboratorio")>0) {
						System.err.println("En la pagina del laboratorio");
					} else if (data.indexOf("Credenciales")>0) {
						System.err.println("Login Incorrecto (SIU-UPM)");
					}else if (data.indexOf("LDAP-DIT")>0) {
						System.err.println("Login incorrecto (LDAP-DIT)");
						browser.setUrl(NET_URL+"?Invalid=1");
					} else if (data.indexOf("displayName")>0) {
						String nombre="";
						String login="";
						String userid="";
						System.err.println("Login success");
						try {
							Hashtable<String,String> items =retrieveData(data);
							Enumeration<String> e = items.keys();
							// now some checks
							if (!items.get("upmCentre").contains("09"))
								throw new IOException("El usuario "+items.get("uid")+" no está en la ETSIT");
							while (e.hasMoreElements()) {
								String key=e.nextElement();
								System.err.println(key+": "+items.get(key));
								if (key.equals("displayName")) nombre=items.get(key);
								if (key.equals("uid")) login=items.get(key);
							}
							// also extract DNI
							String dni=items.get("upmPersonalUniqueID").replaceAll("[^0-9]", "");
							System.out.println("UserID: "+dni);
							userid=dni;
							// componemos la url
							String url=SUCCESS_URL+"?nombre="+nombre+"&login="+login+"&userid="+userid;
							browser.setUrl(url);
							delayedClose(7000);
							// PENDING need to launch gnome session
						} catch (Exception e) {
							System.err.println(e.getMessage());
							e.printStackTrace();
						}
					} else {
						System.err.println("En la página de autenticación de la UPM");
					}
				}
            });
        } catch (SWTError e) {
            System.err.println("No se puede instanciar Browser: " + e.getMessage()+". Tu instalación de eclipse no permite acceder a moodle");
            e.printStackTrace();
            display.dispose();
            return;
        }

		// generamos la pantallita
        final Composite composite = new Composite(shell, SWT.NONE);
        FormLayout compLayout = new FormLayout();
        composite.setLayout(compLayout);

		// ventana del navegador
        FormData data = new FormData();
		data.left = new FormAttachment(0, 0);
		data.bottom = new FormAttachment(100,0);
		data.right = new FormAttachment(100, 0);
		data.top = new FormAttachment(composite, 0, SWT.DEFAULT);
		browser.setLayoutData(data);

    }

	private void delayedClose(int milis) {
		Display.getDefault().timerExec(milis,new Runnable() {
			public void run() {
				try {
					display.dispose();
				} catch(Exception te) {
					System.err.println(te.getMessage());
					te.printStackTrace();
				}
			}
		});
	}

    private boolean isInternetReachable() {
        HttpURLConnection urlConnect = null;
        try {
            // make a URL to a known source
            URL url = new URL(NET_URL);
            // open a connection to that source
            urlConnect = (HttpURLConnection) url.openConnection();
            // trying to retrieve data from the source. If there is no connection, this line will fail
            urlConnect.getContent();
        } catch (IOException e) {
			System.err.println(e.getMessage());
            return false;
        } finally {
            // cleanup
            if(urlConnect != null) urlConnect.disconnect();
        }
        return true;
    }
    
    public void start() {
        shell.open();
        if(isInternetReachable()) browser.setUrl(NET_URL);
        else {
        	System.err.println("No está accesible "+NET_URL+": o el servidor no está accesible o no hay acceso a internet");
        	display.dispose();
        	return;
        }
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        display.dispose();
    }

    static {
		try {
    		String os=getOS();
    		if (os.equals("osx")) {
        		Class atsc = Class.forName("org.eclipse.swt.internal.cocoa.NSDictionary");
        		Class nsstring = Class.forName("org.eclipse.swt.internal.cocoa.NSString");
        		Class cid = Class.forName("org.eclipse.swt.internal.cocoa.id");
        		Method mdictionaryWithObject = atsc.getDeclaredMethod("dictionaryWithObject", cid,cid);
				Object arg1 = Class.forName("org.eclipse.swt.internal.cocoa.NSNumber").getDeclaredMethod("numberWithBool", Boolean.TYPE).invoke(null, true);
				Object arg2 = Class.forName("org.eclipse.swt.internal.cocoa.NSString").getDeclaredMethod("stringWith", String.class).invoke(null, "NSAllowsArbitraryLoads");
				Object ats = mdictionaryWithObject.invoke(null,arg1,arg2);
				Class cnsBundle=Class.forName("org.eclipse.swt.internal.cocoa.NSBundle");
				Object mainBundle = cnsBundle.getDeclaredMethod("mainBundle").invoke(null);
				Object diccio = cnsBundle.getMethod("infoDictionary").invoke(mainBundle);
				Object arg3 = Class.forName("org.eclipse.swt.internal.cocoa.NSString").getDeclaredMethod("stringWith", String.class).invoke(null, "NSAppTransportSecurity");
				Class.forName("org.eclipse.swt.internal.cocoa.NSObject").getMethod("setValue", cid, nsstring).invoke(diccio, ats, arg3);
			}
    		/*
    		org.eclipse.swt.internal.cocoa.NSDictionary ats = org.eclipse.swt.internal.cocoa.NSDictionary.dictionaryWithObject(
	 	           org.eclipse.swt.internal.cocoa.NSNumber.numberWithBool(true),
	 	           org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAllowsArbitraryLoads"));
	 	   org.eclipse.swt.internal.cocoa.NSBundle.mainBundle().infoDictionary().setValue(
	 	           ats, org.eclipse.swt.internal.cocoa.NSString.stringWith("NSAppTransportSecurity"));
	 	   */
    	} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}
    
    private static String getOS() {
 	   String osNameProperty = System.getProperty("os.name");
 	   if (osNameProperty == null) {
 	       System.err.println("os.name property no está actulizado"); 
 	       return null;
 	   } else {
		   osNameProperty = osNameProperty.toLowerCase();
	   }
 	   if (osNameProperty.contains("win"))   return "win";
 	   else if (osNameProperty.contains("mac")) return "osx";
 	   else if (osNameProperty.contains("linux") || osNameProperty.contains("nix")) return "linux";
 	   else {
 	       System.err.println("SO desconocido " + osNameProperty); 
 	       return null;
 	   } 
    }
	
    public static void main(String[] args) {
		boolean fullScreen= (args.length > 0) && (args[0].equals("fullscreen"));
		new UPMAuthenticator(fullScreen).start();
    }
}
