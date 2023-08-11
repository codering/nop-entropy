package io.nop.file.dao.store;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.io.stream.LimitedInputStream;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceStore;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.InputStreamResource;
import io.nop.core.resource.store.LocalResourceStore;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.file.core.FileConstants;
import io.nop.file.core.IFileRecord;
import io.nop.file.core.IFileStore;
import io.nop.file.core.UploadRequestBean;
import io.nop.file.dao.entity.NopFileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Objects;

import static io.nop.file.core.FileErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.file.core.FileErrors.ARG_FILE_ID;
import static io.nop.file.core.FileErrors.ARG_FILE_OBJ_NAME;
import static io.nop.file.core.FileErrors.ARG_LENGTH;
import static io.nop.file.core.FileErrors.ARG_MAX_LENGTH;
import static io.nop.file.core.FileErrors.ERR_FILE_ATTACH_FILE_NOT_SAME_OBJ;
import static io.nop.file.core.FileErrors.ERR_FILE_LENGTH_EXCEED_LIMIT;

/**
 * 将上传文件存放在本地目录下
 */
public class DaoResourceFileStore implements IFileStore {
    static final Logger LOG = LoggerFactory.getLogger(DaoResourceFileStore.class);
    private IDaoProvider daoProvider;

    private IResourceStore resourceStore;

    @PostConstruct
    public void init() {
        Guard.notNull(resourceStore, "resourceStore");
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setResourceStore(IResourceStore resourceStore) {
        this.resourceStore = resourceStore;
    }

    public void setLocalDir(File dir) {
        this.resourceStore = new LocalResourceStore("/", dir);
    }

    @Override
    public String getFileLink(String fileId) {
        return FileConstants.PATH_DOWNLOAD + "/" + fileId;
    }

    @Override
    public String decodeFileId(String fileLink) {
        if (StringHelper.isEmpty(fileLink))
            return null;
        if (fileLink.startsWith(FileConstants.PATH_DOWNLOAD))
            return StringHelper.lastPart(fileLink, '/');
        return null;
    }

    @Override
    public IFileRecord getFile(String fileId) {
        NopFileRecord record = daoProvider.daoFor(NopFileRecord.class).requireEntityById(fileId);
        IResource resource = resourceStore.getResource(record.getFilePath());
        return new DaoFileRecord(record, resource);
    }

    public String saveFile(UploadRequestBean record, long maxLength) {
        checkMaxSize(record.getLength(), maxLength);

        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord entity = dao.newEntity();
        entity.setFileName(record.getFileName());
        entity.setFileExt(record.getFileExt());
        entity.setFileLength(record.getLength());
        // 标记为临时对象。如果最终没有提交，则会应该自动删除这些记录
        entity.setBizObjId(FileConstants.TEMP_BIZ_OBJ_ID);
        entity.setBizObjName(record.getBizObjName());
        entity.setMimeType(record.getMimeType());
        if (StringHelper.isEmpty(entity.getMimeType()))
            entity.setMimeType(MediaType.APPLICATION_OCTET_STREAM);

        String fileId = newFileId();
        String filePath = newPath(record.getBizObjName(), fileId);
        entity.setFileId(fileId);
        entity.setFilePath(filePath);

        IResource tempResource = null;
        InputStream is = null;
        try {
            is = record.getInputStream();
            if (record.getLength() > 0) {
                filePath = resourceStore.saveResource(filePath, new InputStreamResource(filePath, is, record.getLastModified(), record.getLength()), null, null);
            } else {
                tempResource = ResourceHelper.getTempResource("upload-file");
                if (maxLength > 0) {
                    is = new LimitedInputStream(is, maxLength);
                }
                ResourceHelper.writeStream(tempResource, is);
                filePath = resourceStore.saveResource(filePath, tempResource, null, null);
            }

            entity.setFilePath(filePath);
            dao.saveEntity(entity);
            return entity.getFileId();
        } catch (Exception e) {
            removeResource(filePath);
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(is);
            if (tempResource != null)
                tempResource.delete();
        }
    }

    protected void checkMaxSize(long length, long maxFileSize) {
        if (maxFileSize > 0 && length > maxFileSize)
            throw new NopException(ERR_FILE_LENGTH_EXCEED_LIMIT)
                    .param(ARG_LENGTH, length).param(ARG_MAX_LENGTH, maxFileSize);
    }


    private boolean removeResource(String path) {
        try {
            IResource resource = resourceStore.getResource(path);
            return resource.delete();
        } catch (Exception e) {
            LOG.error("nop.file.remove-file-fail:path={}", path, e);
            return false;
        }
    }

    protected String newFileId() {
        return StringHelper.generateUUID();
    }

    protected String newPath(String bizObjName, String fileId) {
        LocalDate now = DateHelper.currentDate();
        StringBuilder sb = new StringBuilder();
        sb.append('/').append(bizObjName);
        sb.append("/").append(StringHelper.leftPad(String.valueOf(now.getYear()), 4, '0'));
        sb.append('/').append(StringHelper.leftPad(String.valueOf(now.getMonthValue()), 2, '0'));
        sb.append('/').append(StringHelper.leftPad(String.valueOf(now.getDayOfMonth()), 2, '0'));
        sb.append('/').append(fileId);
        return sb.toString();
    }

    @Override
    public void detachFile(String fileId, String bizObjName, String objId, String fieldName) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.getEntityById(fileId);
        if (record != null) {
            if (Objects.equals(record.getBizObjName(), bizObjName) && Objects.equals(record.getBizObjId(), objId)) {
                dao.deleteEntity(record);
                removeResource(record.getFilePath());
            } else {
                LOG.warn("nop.file.record-not-attached-to-object:fileId={},bizObjName={},objId={},attachedObjName={},attachedObjId={}", fileId, bizObjName, objId, record.getBizObjName(), record.getBizObjId());
            }
        }
    }

    @Override
    public void attachFile(String fileId, String bizObjName, String objId, String fieldName) {
        IEntityDao<NopFileRecord> dao = daoProvider.daoFor(NopFileRecord.class);
        NopFileRecord record = dao.requireEntityById(fileId);
        if (!Objects.equals(record.getBizObjName(), bizObjName))
            throw new NopException(ERR_FILE_ATTACH_FILE_NOT_SAME_OBJ)
                    .param(ARG_FILE_ID, fileId).param(ARG_BIZ_OBJ_NAME, bizObjName).param(ARG_FILE_OBJ_NAME, record.getBizObjName());
        record.setBizObjId(objId);
        record.setFieldName(fieldName);
        dao.updateEntity(record);
    }
}