/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modeshape.jcr.spi.federation;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.NamespaceRegistry;
import javax.jcr.RepositoryException;
import javax.transaction.TransactionManager;
import org.modeshape.schematic.DocumentFactory;
import org.modeshape.schematic.document.Document;
import org.modeshape.common.logging.Logger;
import org.modeshape.common.text.TextDecoder;
import org.modeshape.common.util.CheckArg;
import org.modeshape.jcr.Environment;
import org.modeshape.jcr.ExecutionContext;
import org.modeshape.jcr.JcrI18n;
import org.modeshape.jcr.JcrLexicon;
import org.modeshape.jcr.api.nodetype.NodeTypeManager;
import org.modeshape.jcr.cache.DocumentAlreadyExistsException;
import org.modeshape.jcr.cache.DocumentNotFoundException;
import org.modeshape.jcr.cache.document.DocumentTranslator;
import org.modeshape.jcr.federation.FederatedDocumentReader;
import org.modeshape.jcr.federation.FederatedDocumentWriter;
import org.modeshape.jcr.mimetype.MimeTypeDetector;
import org.modeshape.jcr.value.Name;
import org.modeshape.jcr.value.NameFactory;
import org.modeshape.jcr.value.Path;
import org.modeshape.jcr.value.PathFactory;
import org.modeshape.jcr.value.Property;
import org.modeshape.jcr.value.PropertyFactory;
import org.modeshape.jcr.value.ValueFactories;
import org.modeshape.jcr.value.ValueFormatException;
import org.modeshape.jcr.value.binary.ExternalBinaryValue;
import org.modeshape.jcr.value.binary.UrlBinaryValue;

/**
 * SPI of a generic external connector, representing the interface to an external system integrated with ModeShape. Since it is
 * expected that the documents are well formed (structure-wise), the {@link FederatedDocumentWriter} class should be used. This is
 * the base class for {@link WritableConnector} and {@link ReadOnlyConnector} which is what connector implementations are expected
 * to implement.
 *
 * @author Horia Chiorean (hchiorea@redhat.com)
 */
public abstract class Connector {

    protected static final String DEFAULT_ROOT_ID = "/";
    
    /**
     * The logger instance, set via reflection
     *
     * @see #simpleLogger
     */
    private Logger logger;

    /**
     * The simpler API logger instance, set via reflection
     *
     * @see #logger
     */
    private org.modeshape.jcr.api.Logger simpleLogger;

    /**
     * The name of this connector, set via reflection immediately after instantiation.
     */
    private String name;

    /**
     * The name of the repository that owns this connector, set via reflection immediately after instantiation.
     */
    private String repositoryName;

    /**
     * The execution context, set via reflection before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     */
    private ExecutionContext context;

    /**
     * The MIME type detector, set via reflection before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     */
    private MimeTypeDetector mimeTypeDetector;

    /**
     * A flag which indicates whether documents returned by this connector should be cached by the repository or not.
     * <p>
     * Documents are cached by default, just like regular nodes, in the {@code WorkspaceCache}. However, if this causes stale
     * data for a connector, this can implement its own caching logic and disable repository caching altogether.
     * </p>
     */
    private boolean cacheable = true;

    /**
     * A flag which indicates whether content exposed by this connector should be indexed or not by the repository. This acts as a
     * global flag, allowing a connector to mark it's entire content as non-queryable. By default, all content is queryable.
     * <p>
     * The field is assigned via reflection based upon the configuration of the external source represented by this connector
     * before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     * </p>
     */
    private boolean queryable = true;

    private boolean initialized = false;

    /**
     * A document translator that is used within the DocumentReader implementation, but which has no DocumentStore reference and
     * thus is not fully-functional.
     * <p>
     * The field is assigned via reflection before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     * </p>
     */
    private DocumentTranslator translator;

    /**
     * A property store that the connector can use to persist "extra" properties that cannot be stored in the external system. The
     * use of this store is optional, and connectors should store as much information as possible in the external system.
     * Connectors are also responsible for removing the extra properties for a node when it is removed.
     * <p>
     * The field is assigned via reflection before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     * </p>
     */
    private ExtraPropertiesStore extraPropertiesStore;

    /**
     * The {@link TransactionManager} instance used by the repository, which allows a connector to enroll, via an
     * {@link javax.transaction.xa.XAResource} implementation into existing transactions. It's up to the connector to implement
     * the logic for determining transaction boundaries and to process documents accordingly.
     * <p>
     * The field is assigned via reflection before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     * </p>
     */
    private TransactionManager transactionManager;

    /**
     * The {@link ConnectorChangeSetFactory} instance which allows a connector to create {@link ConnectorChangeSet}s.
     * <p>
     * The field is assigned via reflection before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     * </p>
     */
    private ConnectorChangeSetFactory connectorChangedSetFactory;

    /**
     * The {@link Environment} instance in which the repository operates. This should be used by connectors which need to perform
     * class-path related operations.
     * <p>
     * The field is assigned via reflection before ModeShape calls {@link #initialize(NamespaceRegistry, NodeTypeManager)}.
     * </p>
     */
    private Environment environment;

    /**
     * Ever connector is expected to have a no-argument constructor, although the class should never initialize any of the data at
     * this time. Instead, all initialization should be performed in the {@link #initialize} method.
     */
    public Connector() {
    }

    /**
     * Returns the name of the source which this connector interfaces with.
     *
     * @return a {@code non-null} string.
     */
    public String getSourceName() {
        return name;
    }

    /**
     * Get the name of the repository.
     *
     * @return the repository name; never null
     */
    public final String getRepositoryName() {
        return repositoryName;
    }

    /**
     * Get the I18n-compatible logger for this connector instance. This is available for use in or after the
     * {@link #initialize(NamespaceRegistry, NodeTypeManager)} method.
     * <p>
     * This logger is a bit more complicated than the {@link #log() simple logger} because it requires use of ModeShape's
     * internationalization and localization framework. All of ModeShape's code uses this I18n framework, making it much easier to
     * localize ModeShape to other languages. Using this logger would make it just as easy to localize the Connector
     * implementation. But since it is more complicated, it may not be for every developer. Subclasses can use this logger or the
     * simpler logger; you can even mix and match (though we'd recommend that you just pick one and only use it).
     * <p>
     *
     * @return the logger that requires using ModeShape I18n framework; never null
     * @see #log()
     */
    public final Logger getLogger() {
        return logger;
    }

    /**
     * Get the simpler, String-based logger for this connector instance. This is available for use in or after the
     * {@link #initialize(NamespaceRegistry, NodeTypeManager)} method.
     * <p>
     * This logger is much simpler than the {@link #getLogger() I18n-compatible logger}, especially since all of the log messages
     * take simple strings. However, this simple logger is not easily internationalized. Subclasses can use this logger or the
     * more formal, I18n-compatible logger; you can even mix and match (though we'd recommend that you just pick one and only use
     * it).
     * <p>
     *
     * @return the simple logger that requires String messages; never null
     * @see #getLogger()
     */
    public final org.modeshape.jcr.api.Logger log() {
        return simpleLogger;
    }

    /**
     * Get the execution context for this connector instance. This is available for use in or after the
     * {@link #initialize(NamespaceRegistry, NodeTypeManager)} method.
     *
     * @return the context; never null
     */
    public ExecutionContext getContext() {
        return context;
    }

    /**
     * Get the MIME type detector for this connector instance. This is available for use in or after the
     * {@link #initialize(NamespaceRegistry, NodeTypeManager)} method.
     *
     * @return the MIME type detector; never null
     */
    public MimeTypeDetector getMimeTypeDetector() {
        return mimeTypeDetector;
    }
    
    /**
     * Returns whether documents exposed by this connector should be cached by the repository or not.
     * 
     * @return {@code true} if documents should be cached, {@code false} otherwise.
     */
    public boolean isCacheable() {
        return cacheable;
    }

    /**
     * Indicates if content exposed by this connector should be indexed by the repository or not.
     *
     * @return {@code true} if the content should be indexed, {@code false} otherwise.
     */
    public Boolean isQueryable() {
        return queryable;
    }

    protected ExtraProperties extraPropertiesFor( String id,
                                                  boolean update ) {
        return new ExtraProperties(id, update);
    }

    /**
     * Get the "extra" properties store. Connectors can directly use this, although it's probably easier to
     * {@link #extraPropertiesFor(String,boolean) create} an {@link ExtraProperties} object for each node and use it to
     * {@link ExtraProperties#add(Property) add}, {@link ExtraProperties#remove(Name) remove} and then
     * {@link ExtraProperties#save() store or update} the extra properties in the extra properties store.
     *
     * @return the storage for extra properties; never null
     */
    protected ExtraPropertiesStore extraPropertiesStore() {
        return extraPropertiesStore;
    }

    /**
     * Method that can be called by a connector during {@link #initialize(NamespaceRegistry, NodeTypeManager) initialization} if
     * it wants to provide its own implementation of an "extra" properties store.
     *
     * @param customExtraPropertiesStore the custom implementation of the ExtraPropertiesStore; may not be null
     */
    protected void setExtraPropertiesStore( ExtraPropertiesStore customExtraPropertiesStore ) {
        CheckArg.isNotNull(customExtraPropertiesStore, "customExtraPropertiesStore");
        this.extraPropertiesStore = customExtraPropertiesStore;
    }

    /**
     * Moves a set of extra properties from an old to a new node after their IDs have changed.
     *
     * @param oldNodeId the old identifier for the node; may not be null
     * @param newNodeId the new identifier for the node; may not be null
     */
    protected void moveExtraProperties( String oldNodeId,
                                        String newNodeId ) {
        ExtraPropertiesStore extraPropertiesStore = extraPropertiesStore();
        if (extraPropertiesStore == null || !extraPropertiesStore.contains(oldNodeId)) {
            return;
        }
        Map<Name, Property> existingExtraProps = extraPropertiesStore.getProperties(oldNodeId);
        extraPropertiesStore.removeProperties(oldNodeId);
        extraPropertiesStore.storeProperties(newNodeId, existingExtraProps);
    }

    /**
     * Returns the transaction manager instance that was set on the connector during initialization.
     *
     * @return a {@code non-null} {@link TransactionManager} instance
     */
    protected TransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Returns the repository environment that was set during connector initialization
     *
     * @return a {@code non-null} {@link Environment} instace.
     */
    protected Environment getEnvironment() {
        return environment;
    }

    /**
     * Returns the connector-specific ID for the root document. Connectors are free to ignore this method unless they 
     * require their content to be browsable in a workspace (see https://issues.jboss.org/browse/MODE-1712)
     * 
     * @return a {@code String} representing the ID of the root document.
     * @see #getDocumentById(String) 
     */
    public String getRootDocumentId() {
        return DEFAULT_ROOT_ID;        
    }

    /**
     * Initialize the connector. This is called automatically by ModeShape once for each Connector instance, and should not be
     * called by the connector. By the time this method is called, ModeShape will hav already set the {@link #context},
     * {@link #logger}, {@link #name}, and {@link #repositoryName} plus any fields that match configuration properties for the
     * connector.
     * <p>
     * By default this method does nothing, so it should be overridden by implementations to do a one-time initialization of any
     * internal components. For example, connectors can use the supplied <code>registry</code> and <code>nodeTypeManager</code>
     * objects to register custom namesapces and node types required by the external content.
     * </p>
     * <p>
     * This is an excellent place for connector to validate the connector-specific fields set by ModeShape via reflection during
     * instantiation.
     * </p>
     *
     * @param registry the namespace registry that can be used to register custom namespaces; never null
     * @param nodeTypeManager the node type manager that can be used to register custom node types; never null
     * @throws RepositoryException if operations on the {@link NamespaceRegistry} or {@link NodeTypeManager} fail
     * @throws IOException if any stream based operations fail (like importing cnd files)
     */
    public void initialize( NamespaceRegistry registry,
                            NodeTypeManager nodeTypeManager ) throws RepositoryException, IOException {
        // Subclasses may not necessarily call 'super.initialize(...)', but if they do then we can make this assertion ...
        assert !initialized : "The Connector.initialize(...) method should not be called by subclasses; ModeShape has already (and automatically) initialized the Connector";
    }

    /**
     * Method called by the code calling {@link #initialize} (typically via reflection) to signal that the initialize method is
     * completed. See initialize() for details, and no this method is indeed used.
     */
    @SuppressWarnings( "unused" )
    private void postInitialize() {
        if (!initialized) {
            initialized = true;

            // ------------------------------------------------------------------------------------------------------------
            // Add any code here that needs to run after #initialize(...), which will be overwritten by subclasses
            // ------------------------------------------------------------------------------------------------------------
        }
    }

    /**
     * Shutdown the connector by releasing all resources. This is called automatically by ModeShape when this Connector instance
     * is no longer needed, and should never be called by the connector.
     */
    public void shutdown() {
        // do nothing by default
    }

    /**
     * Returns a {@link Document} instance representing the document with a given id. The document should have a "proper"
     * structure for it to be usable by ModeShape.
     *
     * @param id a {@code non-null} string
     * @return either an {@link Document} instance or {@code null}
     */
    public abstract Document getDocumentById( String id );

    /**
     * Returns the id of an external node located at the given external path within the connector's exposed tree of content.
     *
     * @param externalPath a {@code non-null} string representing an external path, or "/" for the top-level node exposed by the
     *        connector
     * @return either the id of the document or {@code null}
     */
    public abstract String getDocumentId( String externalPath );

    /**
     * Return the path(s) of the external node with the given identifier. The resulting paths are from the point of view of the
     * connector. For example, the "root" node exposed by the connector wil have a path of "/".
     *
     * @param id a {@code non-null} string
     * @return the connector-specific path(s) of the node, or an empty document if there is no such document; never null
     */
    public abstract Collection<String> getDocumentPathsById( String id );

    /**
     * Indicates if the connector instance has been configured in read-only mode.
     *
     * @return {@code true} if the connector has been configured in read-only mode, false otherwise.
     */
    public abstract boolean isReadonly();

    /**
     * Returns a document representing a single child reference from the supplied parent to the supplied child. This method is
     * called when there are an unknown number of children on a node.
     * <p>
     * This method should be implemented and will be called if and only if a {@link Pageable connector uses paging} and specifies
     * an {@link PageWriter#UNKNOWN_TOTAL_SIZE unknown number of children} in the
     * {@link PageWriter#addPage(String, int, long, long)} or {@link PageWriter#addPage(String, String, long, long)} methods.
     * </p>
     *
     * @param parentKey the key for the parent
     * @param childKey the key for the child
     * @return the document representation of a child reference, of null if the parent does not contain a child with the given key
     */
    public Document getChildReference( String parentKey,
                                       String childKey ) {
        return null;
    }

    /**
     * Returns a binary value which is connector specific and which is never stored by ModeShape. Connectors who need this feature
     * must return an object that is an instance of a subclasses of {@link ExternalBinaryValue}, either {@link UrlBinaryValue} or
     * a custom subclass with connector-specific information.
     * <p>
     * Normally, the {@link #getDocumentById(String)} method implementation will set binary values on properties of nodes, which
     * should create the same ExternalBinaryValue subclass that is returned by this method. The
     * {@link ExternalBinaryValue#getId()} value from that instance will be passed into this method.
     * </p>
     *
     * @param id a {@code String} representing the identifier of the external binary which should have connector-specific meaning.
     *        This identifier need not be the SHA-1 hash of the content.
     * @return either a binary value implementation or {@code null} if there is no such value with the given id.
     */
    public ExternalBinaryValue getBinaryValue( String id ) {
        return null;
    }

    /**
     * Removes the document with the given id.
     *
     * @param id a {@code non-null} string.
     * @return true if the document was removed, or false if there was no document with the given id
     */
    public abstract boolean removeDocument( String id );

    /**
     * Checks if a document with the given id exists in the end-source.
     *
     * @param id a {@code non-null} string.
     * @return {@code true} if such a document exists, {@code false} otherwise.
     */
    public abstract boolean hasDocument( String id );

    /**
     * Stores the given document.
     *
     * @param document a {@code non-null} {@link Document} instance.
     * @throws DocumentAlreadyExistsException if there is already a new document with the same identifier
     * @throws DocumentNotFoundException if one of the modified documents was removed by another session
     */
    public abstract void storeDocument( Document document );

    /**
     * Updates a document using the provided changes.
     *
     * @param documentChanges a {@code non-null} {@link DocumentChanges} object which contains granular information about all the
     *        changes.
     */
    public abstract void updateDocument( DocumentChanges documentChanges );

    /**
     * Generates an identifier which will be assigned when a new document (aka. child) is created under an existing document
     * (aka.parent). This method should be implemented only by connectors which support writing.
     *
     * @param parentId a {@code non-null} {@link String} which represents the identifier of the parent under which the new
     *        document will be created.
     * @param newDocumentName a {@code non-null} {@link org.modeshape.jcr.value.Name} which represents the name that will be given
     *        to the child document
     * @param newDocumentPrimaryType a {@code non-null} {@link org.modeshape.jcr.value.Name} which represents the child document's
     *        primary type.
     * @return either a {@code non-null} {@link String} which will be assigned as the new identifier, or {@code null} which means
     *         that no "special" id format is required. In this last case, the repository will auto-generate a random id.
     * @throws org.modeshape.jcr.cache.DocumentStoreException if the connector is readonly.
     */
    public abstract String newDocumentId( String parentId,
                                          Name newDocumentName,
                                          Name newDocumentPrimaryType );

    /**
     * Utility method that checks whether the field with the supplied name is set.
     *
     * @param fieldValue the value of the field
     * @param fieldName the name of the field
     * @throws RepositoryException if the field value is null
     */
    protected void checkFieldNotNull( Object fieldValue,
                                      String fieldName ) throws RepositoryException {
        if (fieldValue == null) {
            throw new RepositoryException(JcrI18n.requiredFieldNotSetInConnector.text(getSourceName(), getClass(), fieldName));
        }
    }

    protected DocumentTranslator translator() {
        return translator;
    }

    /**
     * Obtain a new {@link DocumentReader} that can be used to read an existing document, typically used within the
     * {@link #storeDocument(Document)} and {@link #updateDocument(DocumentChanges)} methods.
     *
     * @param document the document that should be read; may not be null
     * @return the document reader; never null
     */
    protected DocumentReader readDocument( Document document ) {
        return new FederatedDocumentReader(translator, document);
    }

    /**
     * Obtain a new {@link DocumentWriter} that can be used to construct a document, typically within the
     * {@link #getDocumentById(String)} method.
     *
     * @param id the identifier of the document; may not be null
     * @return the document writer; never null
     */
    protected DocumentWriter newDocument( String id ) {
        return new FederatedDocumentWriter(translator).setId(id);
    }

    /**
     * Obtain a new {@link DocumentWriter} that can be used to update a document.
     *
     * @param document the document that should be updated; may not be null
     * @return the document writer; never null
     */
    protected DocumentWriter writeDocument( Document document ) {
        return new FederatedDocumentWriter(translator, document);
    }

    /**
     * Obtain a new {@link PageWriter} that can be used to construct a page of children, typically within the
     * {@link Pageable#getChildren(PageKey)} method.
     *
     * @param pageKey the key for the page; may not be null
     * @return the page writer; never null
     */
    protected PageWriter newPageDocument( PageKey pageKey ) {
        return new FederatedDocumentWriter(translator).setId(pageKey.toString());
    }

    /**
     * Obtain a new child reference document that is useful in the {@link #getChildReference(String, String)} method.
     *
     * @param childId the ID of the child node; may not be null
     * @param childName the name of the child node; may not be null
     * @return the child reference document; never null
     */
    protected Document newChildReference( String childId,
                                          String childName ) {
        return DocumentFactory.newDocument(DocumentTranslator.KEY, childId, DocumentTranslator.NAME, childName);
    }

    /**
     * Get the set of value factory objects that the connector can use to create property value objects.
     *
     * @return the collection of factories; never null
     */
    protected final ValueFactories factories() {
        return context.getValueFactories();
    }

    protected final PropertyFactory propertyFactory() {
        return context.getPropertyFactory();
    }

    protected final PathFactory pathFactory() {
        return factories().getPathFactory();
    }

    /**
     * Helper method that creates a {@link Path} object from a string. This is equivalent to calling "
     * <code>pathFactory().create(path)</code>", and is simply provided for convenience.
     *
     * @param path the string from which the path is to be created
     * @return the value, or null if the supplied string is null
     * @throws ValueFormatException if the conversion from a string could not be performed
     * @see PathFactory#create(String)
     * @see #pathFrom(Path, String)
     */
    protected final Path pathFrom( String path ) {
        return factories().getPathFactory().create(path);
    }

    /**
     * Helper method that creates a {@link Path} object from a parent path and a child path string. This is equivalent to calling
     * " <code>pathFactory().create(parentPath,childPath)</code>", and is simply provided for convenience.
     *
     * @param parentPath the parent path
     * @param childPath the child path as a string
     * @return the value, or null if the supplied string is null
     * @throws ValueFormatException if the conversion from a string could not be performed
     * @see PathFactory#create(String)
     * @see #pathFrom(String)
     */
    protected final Path pathFrom( Path parentPath,
                                   String childPath ) {
        Path parent = pathFactory().create(parentPath);
        return pathFactory().create(parent, childPath);
    }

    /**
     * Helper method that creates a {@link Name} object from a string, using no decoding. This is equivalent to calling "
     * <code>factories().getNameFactory().create(nameString)</code>", and is simply provided for convenience.
     *
     * @param nameString the string from which the name is to be created
     * @return the value, or null if the supplied string is null
     * @throws ValueFormatException if the conversion from a string could not be performed
     * @see NameFactory#create(String, TextDecoder)
     * @see NameFactory#create(String, String, TextDecoder)
     * @see NameFactory#create(String, String)
     * @see #nameFrom(String, String)
     * @see #nameFrom(String, String, TextDecoder)
     */
    protected final Name nameFrom( String nameString ) {
        return factories().getNameFactory().create(nameString);
    }

    /**
     * Create a name from the given namespace URI and local name. This is equivalent to calling "
     * <code>factories().getNameFactory().create(namespaceUri,localName)</code>", and is simply provided for convenience.
     *
     * @param namespaceUri the namespace URI
     * @param localName the local name
     * @return the new name
     * @throws IllegalArgumentException if the local name is <code>null</code> or empty
     * @see NameFactory#create(String, TextDecoder)
     * @see NameFactory#create(String, String, TextDecoder)
     * @see NameFactory#create(String, String)
     * @see #nameFrom(String)
     * @see #nameFrom(String, String, TextDecoder)
     */
    protected final Name nameFrom( String namespaceUri,
                                   String localName ) {
        return factories().getNameFactory().create(namespaceUri, localName);
    }

    /**
     * Create a name from the given namespace URI and local name. This is equivalent to calling "
     * <code>factories().getNameFactory().create(namespaceUri,localName,decoder)</code>", and is simply provided for convenience.
     *
     * @param namespaceUri the namespace URI
     * @param localName the local name
     * @param decoder the decoder that should be used to decode the qualified name
     * @return the new name
     * @throws IllegalArgumentException if the local name is <code>null</code> or empty
     * @see NameFactory#create(String, String, TextDecoder)
     * @see NameFactory#create(String, TextDecoder)
     * @see NameFactory#create(String, String, TextDecoder)
     * @see NameFactory#create(String, String)
     * @see #nameFrom(String)
     * @see #nameFrom(String, String)
     */
    protected final Name nameFrom( String namespaceUri,
                                   String localName,
                                   TextDecoder decoder ) {
        return factories().getNameFactory().create(namespaceUri, localName, decoder);
    }

    /**
     * @return a fresh {@link ConnectorChangeSet} for use in recording changes
     */
    protected ConnectorChangeSet newConnectorChangedSet() {
        return connectorChangedSetFactory.newChangeSet();
    }

    public final class ExtraProperties {
        private Map<Name, Property> properties = new HashMap<Name, Property>();
        private final boolean update;
        private final String id;

        protected ExtraProperties( String id,
                                   boolean update ) {
            this.id = id;
            this.update = update;
        }

        public ExtraProperties add( Property property ) {
            this.properties.put(property.getName(), property);
            return this;
        }

        public ExtraProperties addAll( Map<Name, Property> properties ) {
            this.properties.putAll(properties);
            return this;
        }

        public ExtraProperties remove( Name propertyName ) {
            this.properties.put(propertyName, null);
            return this;
        }

        public ExtraProperties remove( String propertyName ) {
            this.properties.put(nameFrom(propertyName), null);
            return this;
        }

        public ExtraProperties except( Name... names ) {
            for (Name name : names) {
                this.properties.remove(name);
            }
            return this;
        }

        public ExtraProperties except( String... names ) {
            for (String name : names) {
                this.properties.remove(nameFrom(name));
            }
            return this;
        }

        public ExtraProperties exceptPrimaryType() {
            this.properties.remove(JcrLexicon.PRIMARY_TYPE);
            return this;
        }

        public void save() {
            if (update) {
                extraPropertiesStore().updateProperties(id, properties);
            } else {
                extraPropertiesStore().storeProperties(id, properties);
            }
            properties.clear();
        }
    }
}
