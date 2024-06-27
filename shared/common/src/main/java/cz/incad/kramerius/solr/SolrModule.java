package cz.incad.kramerius.solr;

import com.google.inject.*;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import cz.incad.kramerius.FedoraAccess;
import cz.incad.kramerius.utils.conf.KConfiguration;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;

/**
 * Created by pstastny on 10/19/2017.
 */
public class SolrModule extends AbstractModule {

    @Override
    protected void configure() {
    }

    @Provides
    @Named("processingQuery")
    @Singleton
    public SolrClient processingQueryClient() {
        String processingSolrHost = KConfiguration.getInstance().getSolrProcessingHost();
        return new HttpSolrClient.Builder(processingSolrHost).build();
    }

    @Provides
    @Named("processingUpdate")
    @Singleton
    public SolrClient processingUpdateClient() {
        String processingSolrHost = KConfiguration.getInstance().getSolrProcessingHost();
        return new ConcurrentUpdateSolrClient.Builder(processingSolrHost).withQueueSize(100).build();
    }

    @Provides
    @Named("proxyUpdate")
    @Singleton
    public SolrClient proxyUpdateClient() {
        String processingSolrHost = KConfiguration.getInstance().getSolrUpdatesHost();
        return new ConcurrentUpdateSolrClient.Builder(processingSolrHost).withQueueSize(100).build();
    }

//    @Provides
//    @Named("proxyReharvest")
//    @Singleton
//    public SolrClient proxyReharvestClient() {
//        String processingSolrHost = KConfiguration.getInstance().getSolrReharvestHost();
//        return new ConcurrentUpdateSolrClient.Builder(processingSolrHost).withQueueSize(100).build();
//    }
}
