package com.xmlcalabash.util;

import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.QName;

import javax.xml.transform.TransformerException;
import javax.xml.transform.SourceLocator;
import javax.xml.transform.ErrorListener;

import com.xmlcalabash.core.XProcException;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcConstants;
import com.xmlcalabash.model.Step;

import net.sf.saxon.trans.XPathException;

import java.net.URI;

/**
 * Created by IntelliJ IDEA.
 * User: ndw
 * Date: Dec 9, 2009
 * Time: 7:13:02 AM
 *
 * This listener collects messages to send to the error port if applicable.
 */
public class StepErrorListener implements ErrorListener {
    private static QName c_error = new QName(XProcConstants.NS_XPROC_STEP, "error");
    private static StructuredQName err_sxxp0003 = new StructuredQName("err", "http://www.w3.org/2005/xqt-errors", "SXXP0003");
    private static QName _type = new QName("", "type");
    private static QName _href = new QName("", "href");
    private static QName _line = new QName("", "line");
    private static QName _column = new QName("", "column");
    private static QName _code = new QName("", "code");
    private static QName _name = new QName("", "name");

    private XProcRuntime runtime = null;
    private URI baseURI = null;

    public StepErrorListener(XProcRuntime runtime) {
        super();
        this.runtime = runtime;
        baseURI = runtime.getStaticBaseURI();
    }

    public void error(TransformerException exception) throws TransformerException {
        if (!report("error", exception)) {
            runtime.error(exception);
        }
    }

    public void fatalError(TransformerException exception) throws TransformerException {
        if (!report("fatal-error", exception)) {
            runtime.error(exception);
        }
    }

    public void warning(TransformerException exception) throws TransformerException {
        if (!report("warning", exception)) {
            // XProc doesn't have recoverable exceptions...
            runtime.warning(exception);
        }
    }

    private boolean report(String type, TransformerException exception) {
        // HACK!!!
        if (runtime.transparentJSON() && exception instanceof XPathException) {
            XPathException e = (XPathException) exception;
            StructuredQName errqn = e.getErrorCodeQName();
            if (errqn != null && errqn.equals(err_sxxp0003)) {
                // We'll be trying again as JSON, so let it go this time
                return true;
            }
        }

        SourceLocator loc = exception.getLocator();
        StructuredQName qCode = null;
        StructuredQName qType = null;
        TreeWriter writer = new TreeWriter(runtime);

        writer.startDocument(baseURI);
        writer.addStartElement(c_error);

        
        
        // read error properties from XProcException
        if (exception.getException() instanceof XProcException) {
        	String name = "unknown";
        	XProcException e = (XProcException) exception.getException();
        	QName eq = e.getErrorCode();
            qCode = new StructuredQName(eq.getPrefix(), eq.getNamespaceURI(), eq.getLocalName());
        	Step s = e.getStep();
        	if(s!=null){
                qType = new StructuredQName(s.getType().getPrefix(), s.getType().getNamespaceURI(), s.getType().getLocalName());
        		name = s.getName();
        	}
            writer.addAttribute(_name, name);
            loc = e.getLocator();
        }

        if(qType==null)qType = new StructuredQName("", "", type);

        writer.addAttribute(_type, qType.getDisplayName());

        String message = exception.toString();
        if (exception instanceof XPathException) {
            XPathException xxx = (XPathException) exception;
            qCode = xxx.getErrorCodeQName();

            Throwable underlying = exception.getException();
            if (underlying == null) {
                underlying = exception.getCause();
            }

            if (underlying != null) {
                message = underlying.toString();
            }
        }
        if (qCode == null && exception.getException() instanceof XPathException) {
            qCode = ((XPathException) exception.getException()).getErrorCodeQName();
        }
        
        
        if (qCode != null) {
            writer.addNamespace(qType.getPrefix(), qType.getURI());
            writer.addNamespace(qCode.getPrefix(), qCode.getURI());
            writer.addAttribute(_code, qCode.getDisplayName());
        }

        if (loc!=null){
            boolean done = false;
            while (!done && loc == null) {
                if (exception.getException() instanceof TransformerException) {
                    exception = (TransformerException) exception.getException();
                    loc = exception.getLocator();
                } else if (exception.getCause() instanceof TransformerException) {
                    exception = (TransformerException) exception.getCause();
                    loc = exception.getLocator();
                } else {
                    done = true;
                }
            }

            if (loc != null) {
                if (loc.getSystemId() != null && !"".equals(loc.getSystemId())) {
                    writer.addAttribute(_href, loc.getSystemId());
                }

                if (loc.getLineNumber() != -1) {
                    writer.addAttribute(_line, ""+loc.getLineNumber());
                }

                if (loc.getColumnNumber() != -1) {
                    writer.addAttribute(_column, ""+loc.getColumnNumber());
                }
            }
        }


        writer.startContent();
        writer.addText(message);
        writer.addEndElement();
        writer.endDocument();

        XdmNode node = writer.getResult();

        return runtime.getXProcData().catchError(node);
    }
}