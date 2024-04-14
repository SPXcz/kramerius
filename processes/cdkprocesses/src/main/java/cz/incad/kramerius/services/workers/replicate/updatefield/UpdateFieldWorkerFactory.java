package cz.incad.kramerius.services.workers.replicate.updatefield;

import com.sun.jersey.api.client.Client;
import cz.incad.kramerius.services.Worker;
import cz.incad.kramerius.services.WorkerFactory;
import cz.incad.kramerius.services.WorkerFinisher;
import cz.incad.kramerius.services.iterators.IterationItem;
import cz.incad.kramerius.services.iterators.ProcessIterator;
import cz.incad.kramerius.services.transform.BasicSourceToDestTransform;
import cz.incad.kramerius.services.transform.SourceToDestTransform;
import cz.incad.kramerius.services.workers.replicate.ReplicateFinisher;
import cz.incad.kramerius.timestamps.TimestampStore;

import org.w3c.dom.Element;

import java.util.List;

public class UpdateFieldWorkerFactory extends WorkerFactory {


    @Override
    public Worker createWorker(String sourceName, ProcessIterator iteratorInstance, Element base, Client client, List<IterationItem> items, WorkerFinisher finisher) {
        return new UpdateFieldWorker(sourceName,base, client, items, finisher);
    }

    @Override
    public WorkerFinisher createFinisher(String timestampString,  Element worker, Client client) {
        return new ReplicateFinisher(timestampString,  worker, client);
    }

    public SourceToDestTransform createTransform() {
        return new BasicSourceToDestTransform();
    }
}
