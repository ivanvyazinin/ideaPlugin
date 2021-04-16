import com.intellij.ide.BrowserUtil;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.stubs.PsiFileStub;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.xml.ui.PsiClassPanel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static com.intellij.ide.lightEdit.LightEditUtil.getProject;
import static org.junit.Assert.assertNotNull;

public class TweetAction extends AnAction {
    @Override
    public void actionPerformed(AnActionEvent event) {
        Editor editor = event.getData(PlatformDataKeys.EDITOR);

        String selectedText = editor.getSelectionModel().getSelectedText();
        PsiJavaFile file = (PsiJavaFile) event.getData(LangDataKeys.PSI_FILE);
        PsiFileStub fileStub = (PsiFileStub) event.getData(LangDataKeys.PSI_FILE);

        event.getData(LangDataKeys.PROJECT);

        String testClass = "import org.junit.jupiter.api.Test; class MyTest {<caret> @Test void m2(){}}";

        if (selectedText != null) {
            String encoded = "";
            try {
                encoded = URLEncoder.encode(selectedText, StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            String url = String.format("https://twitter.com/intent/tweet?text=%s", encoded);
            BrowserUtil.browse(url);
        } else {
            Messages.showMessageDialog("Selection is empty, could you please select something?", "Tweet Action", Messages.getInformationIcon());
        }
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }

}