package cz.incad.kramerius.fedora.om;

import com.qbizm.kramerius.imp.jaxb.DigitalObject;
import cz.incad.kramerius.resourceindex.ProcessingIndexFeeder;

import java.io.IOException;
import java.util.function.Consumer;

public class SynchronizedRepository extends Repository {

    private Repository repository;

    public SynchronizedRepository(Repository repository) {
        this.repository = repository;
    }

    @Override
    public synchronized boolean objectExists(String ident) throws RepositoryException {
        return false;
    }

    @Override
    public synchronized String getBoundContext() throws RepositoryException {
        return this.repository.getBoundContext();
    }

    @Override
    public synchronized void commitTransaction() throws RepositoryException {
        this.repository.commitTransaction();
    }

    @Override
    public synchronized void rollbackTransaction() throws RepositoryException {
        this.repository.rollbackTransaction();
    }

    @Override
    public synchronized RepositoryObject createOrFindObject(String ident) throws RepositoryException {
        return this.repository.createOrFindObject(ident);
    }

    @Override
    public synchronized  RepositoryObject ingestObject(DigitalObject contents) throws RepositoryException {
        return this.repository.ingestObject(contents);
    }

    
//    @Override
//	public RepositoryObject ingestObject(DigitalObject contents, String source) throws RepositoryException {
//        return this.repository.ingestObject(contents, source);
//	}

	@Override
    public synchronized RepositoryObject getObject(String ident) throws RepositoryException {
        return this.repository.getObject(ident);
    }

//    @Override
//    public synchronized void deleteobject(String pid) throws RepositoryException {
//        this.repository.deleteobject(pid);
//    }


    @Override
    public void deleteObject(String pid) throws RepositoryException {
        this.repository.deleteObject(pid);
    }

    @Override
    public void deleteObject(String pid, boolean deleteDataOfManagedDatastreams, boolean deleteRelationsWithThisAsTarget) throws RepositoryException {

    }

    @Override
    public ProcessingIndexFeeder getProcessingIndexFeeder() throws RepositoryException {
        return repository.getProcessingIndexFeeder();
    }

	@Override
	public void iterateObjects(Consumer<String> consumer) throws RepositoryException, IOException {
		// TODO Auto-generated method stub
		
	}
    
    

//    @Override
//    public synchronized void iterateObjects(Consumer<String> consumer) throws RepositoryException, FcrepoOperationFailedException, IOException {
//        this.repository.iterateObjects(consumer);
//    }
}
