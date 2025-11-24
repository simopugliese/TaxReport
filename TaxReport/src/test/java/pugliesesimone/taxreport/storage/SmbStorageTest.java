package pugliesesimone.taxreport.storage;

import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmbStorageTest {

    @Mock SMBClient client;
    @Mock Connection connection;
    @Mock Session session;
    @Mock DiskShare share;
    @Mock File smbFile;

    SmbStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        storage = new SmbStorage("host", "share", "user", "pass");

        Field clientField = SmbStorage.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(storage, client);

        lenient().when(client.connect(anyString())).thenReturn(connection);
        lenient().when(connection.authenticate(any(AuthenticationContext.class))).thenReturn(session);
        lenient().when(session.connectShare(anyString())).thenReturn(share);
    }

    @Test
    void createFolder_ShouldCallMkdir_WhenNotExists() {
        when(share.folderExists(anyString())).thenReturn(false);

        boolean result = storage.createFolder("2024/DOCS");

        assertTrue(result);
        verify(share).mkdir("2024\\DOCS");
    }

    @Test
    void saveFile_ShouldWriteContent() {
        when(share.openFile(anyString(), any(), any(), any(), any(), any())).thenReturn(smbFile);

        String content = "test content";
        InputStream is = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        boolean result = storage.saveFile("2024", "file.txt", is);

        assertTrue(result);
        verify(smbFile, atLeastOnce()).write(any(byte[].class), anyLong(), anyInt(), anyInt());
    }

    @Test
    void loadFile_ShouldReturnStream() {
        when(share.fileExists(anyString())).thenReturn(true);
        when(share.openFile(anyString(), any(), any(), any(), eq(SMB2CreateDisposition.FILE_OPEN), any()))
                .thenReturn(smbFile);

        // [FIX] RIMOSSO lo stubbing di smbFile.read() che causava UnnecessaryStubbingException
        // Il test controlla solo che lo stream venga aperto, non legge nulla.

        InputStream result = storage.loadFile("2024", "file.txt");

        assertNotNull(result);
    }
}