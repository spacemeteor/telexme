package telexme;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.microedition.io.Connector;
import javax.microedition.io.ServerSocketConnection;
import javax.microedition.io.SocketConnection;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import org.bouncycastle.crypto.tls.AlwaysValidVerifyer;
import org.bouncycastle.crypto.tls.DefaultTlsClient;
import org.bouncycastle.crypto.tls.LegacyTlsClient;
import org.bouncycastle.crypto.tls.TlsClient;
import org.bouncycastle.crypto.tls.TlsProtocolHandler;

import telexme.tag.PTwist;

public class TelexMeMIDlet extends MIDlet {

	public TelexMeMIDlet() {
		// TODO Auto-generated constructor stub
	}

	protected void destroyApp(boolean arg0) throws MIDletStateChangeException {
		// TODO Auto-generated method stub

	}

	protected void pauseApp() {
		// TODO Auto-generated method stub

	}

	protected void startApp() throws MIDletStateChangeException {
		// TODO Auto-generated method stub
		try {
//			byte[] test = new byte[7];
//			for (int i = 0; i < test.length; i++) {
//				test[i] = (byte) i;
//			}
//			
//			System.out.println(PTwist.getBigIntegerFromLE(test, 2, 3).toString());
			
			ServerSocketConnection ssc = (ServerSocketConnection) Connector.open("socket://:8888");
			SocketConnection clientsc = (SocketConnection) ssc.acceptAndOpen();
			
			SocketConnection telexsc = (SocketConnection) Connector.open("socket://notblocked.telex.cc:443");
			TlsProtocolHandler tls = new TlsProtocolHandler(telexsc.openInputStream(), telexsc.openOutputStream());
			
			TlsClient tlsClient = new LegacyTlsClient(new AlwaysValidVerifyer());
			tls.connect(tlsClient);
			
			InputStream clientis = clientsc.openInputStream();
			OutputStream telexos = tls.getOutputStream();
			
			int len;
			byte[] buf = new byte[1024];
			while ((len = clientis.read(buf)) != -1) {
				telexos.write(buf, 0, len);
			}
			telexos.flush();
			clientis.close();
			telexos.close();
			
			InputStream telexis = tls.getInputStream();
			OutputStream clientos = clientsc.openOutputStream();
			
			while ((len = telexis.read(buf)) != -1) {
				clientos.write(buf, 0, len);
			}
			clientos.flush();
			telexis.close();
			clientos.close();
			
			telexsc.close();
			clientsc.close();
			ssc.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
