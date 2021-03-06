import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testIntegration.BaseGenerateTestSupportMethodAction;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class GenerateNewTestAction extends BaseGenerateAction {

    protected static final Logger LOG = Logger.getInstance(BaseGenerateTestSupportMethodAction.class);

    public GenerateNewTestAction() {
        super(new MyHandler(TestIntegrationUtils.MethodKind.TEST));
    }

    @Nullable
    private static PsiClass findTargetClass(@NotNull Editor editor, @NotNull PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
        if (containingClass == null) {
            return null;
        }
        final List<TestFramework> frameworks = TestIntegrationUtils.findSuitableFrameworks(containingClass);
        for (TestFramework framework : frameworks) {
            if (framework instanceof JavaTestFramework && ((JavaTestFramework) framework).acceptNestedClasses()) {
                return containingClass;
            }
        }
        return TestIntegrationUtils.findOuterClass(element);
    }

    private static void chooseAndPerform(Editor editor, List<? extends TestFramework> frameworks, final Consumer<? super TestFramework> consumer) {
        if (frameworks.size() == 1) {
            consumer.consume(frameworks.get(0));
            return;
        }

        DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component result = super.getListCellRendererComponent(list, "", index, isSelected, cellHasFocus);
                if (value == null) return result;
                TestFramework framework = (TestFramework) value;

                setIcon(framework.getIcon());
                setText(framework.getName());

                return result;
            }
        };
        JBPopupFactory.getInstance()
                .createPopupChooserBuilder(frameworks)
                .setRenderer(cellRenderer)
                .setNamerForFiltering(o -> o.getName())
                .setTitle(JavaBundle.message("popup.title.choose.framework"))
                .setItemChosenCallback(consumer)
                .setMovable(true)
                .createPopup().showInBestPositionFor(editor);
    }

    @Override
    protected PsiClass getTargetClass(Editor editor, PsiFile file) {
        return findTargetClass(editor, file);
    }

    public static class MyHandler implements CodeInsightActionHandler {
        private final TestIntegrationUtils.MethodKind myMethodKind;

        public MyHandler(TestIntegrationUtils.MethodKind methodKind) {
            myMethodKind = methodKind;
        }

        @Nullable
        private static PsiMethod generateDummyMethod(PsiFile file, Editor editor, PsiClass targetClass) throws IncorrectOperationException {
            final PsiMethod method = TestIntegrationUtils.createDummyMethod(file);
            final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);

            int offset = findOffsetToInsertMethodTo(editor, file, targetClass);
            GenerateMembersUtil.insertMembersAtOffset(file, offset, Collections.singletonList(info));

            final PsiMethod member = info.getPsiMember();
            return member != null ? CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(member) : null;
        }

        private static int findOffsetToInsertMethodTo(Editor editor, PsiFile file, PsiClass targetClass) {
            int result = editor.getCaretModel().getOffset();

            PsiClass classAtCursor = PsiTreeUtil.getParentOfType(file.findElementAt(result), PsiClass.class, false);
            if (classAtCursor == targetClass) {
                return result;
            }

            while (classAtCursor != null && !(classAtCursor.getParent() instanceof PsiFile)) {
                result = classAtCursor.getTextRange().getEndOffset();
                classAtCursor = PsiTreeUtil.getParentOfType(classAtCursor, PsiClass.class);
            }

            return result;
        }

        @Override
        public void invoke(@NotNull Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
            final PsiClass targetClass = findTargetClass(editor, file);
            final List<TestFramework> frameworks = new ArrayList<>(TestIntegrationUtils.findSuitableFrameworks(targetClass));
            for (Iterator<TestFramework> iterator = frameworks.iterator(); iterator.hasNext(); ) {
                if (myMethodKind.getFileTemplateDescriptor(iterator.next()) == null) {
                    iterator.remove();
                }
            }
            if (frameworks.isEmpty()) return;
            final Consumer<TestFramework> consumer = framework -> {
                if (framework == null) return;
                doGenerate(editor, file, targetClass, framework);
            };

            chooseAndPerform(editor, frameworks, consumer);
        }

        private void doGenerate(final Editor editor, final PsiFile file, final PsiClass targetClass, final TestFramework framework) {
            if (framework instanceof JavaTestFramework && ((JavaTestFramework) framework).isSingleConfig()) {
                PsiElement alreadyExist = null;
                switch (myMethodKind) {
                    case SET_UP:
                        alreadyExist = framework.findSetUpMethod(targetClass);
                        break;
                    case TEAR_DOWN:
                        alreadyExist = framework.findTearDownMethod(targetClass);
                        break;
                    default:
                        break;
                }

                if (alreadyExist instanceof PsiMethod) {
                    editor.getCaretModel().moveToOffset(alreadyExist.getNavigationElement().getTextOffset());
                    String message = JavaBundle.message(((PsiMethod) alreadyExist).getName());
                    HintManager.getInstance().showErrorHint(editor, message);
                    return;
                }
            }

            if (!CommonRefactoringUtil.checkReadOnlyStatus(file)) return;

            WriteCommandAction.runWriteCommandAction(file.getProject(), () -> {
                try {
                    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
                    PsiMethod method = generateDummyMethod(file, editor, targetClass);
                    if (method == null) return;

                    TestIntegrationUtils.runTestMethodTemplate(myMethodKind, framework, editor, targetClass, method, "name", false, null);
                } catch (IncorrectOperationException e) {
                    String message = JavaBundle.message(e.getMessage());
                    HintManager.getInstance().showErrorHint(editor, message);
                    LOG.warn(e);
                }
            });
        }
    }
}

