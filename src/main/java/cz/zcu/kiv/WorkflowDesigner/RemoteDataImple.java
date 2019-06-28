package cz.zcu.kiv.WorkflowDesigner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RemoteDataImple extends UnicastRemoteObject implements IRemoteData {
    private static Log logger = LogFactory.getLog(RemoteDataImple.class);

    private BlockData blockData;

    public RemoteDataImple() throws RemoteException {
    }

    @Override
    public BlockData getBlockData (String name) throws RemoteException {
        logger.info("Get BlockData of "+name);
        return blockData;
    }

    @Override
    public void setBlockData(BlockData blockData) throws RemoteException {
        this.blockData = blockData;
    }
}
