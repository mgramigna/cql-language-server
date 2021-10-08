package org.opencds.cqf.cql.ls.provider;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;
import org.opencds.cqf.cql.ls.ActiveContent;
import org.opencds.cqf.cql.ls.CqlUtilities;


// LibrarySourceProvider implementation that pulls from the active content
public class ActiveContentLibrarySourceProvider implements LibrarySourceProvider {
    // private static final Logger LOG = Logger.getLogger("main");

    private final URI baseUri;
    private final ActiveContent activeContent;

    public ActiveContentLibrarySourceProvider(URI baseUri, ActiveContent activeContent) {
        this.baseUri = baseUri;
        this.activeContent = activeContent;
    }

    @Override
    public InputStream getLibrarySource(VersionedIdentifier versionedIdentifier) {
        String id = versionedIdentifier.getId();
        String version = versionedIdentifier.getVersion();

        String matchText = "(?s).*library\\s+" + id;
        if (version != null) {
            matchText += ("\\s+version\\s+'" + version + "'\\s+(?s).*");
        }
        else {
            matchText += "'\\s+(?s).*";
        }

        for(URI uri : this.activeContent.keySet()){

            URI root = CqlUtilities.getHead(uri);
            if (!root.equals(this.baseUri)) {
                continue;
            }
            
            String content = this.activeContent.get(uri).content;
            // This will match if the content contains the library definition is present.
            if (content.matches(matchText)){
                return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }
        }

        return null;
    }
}