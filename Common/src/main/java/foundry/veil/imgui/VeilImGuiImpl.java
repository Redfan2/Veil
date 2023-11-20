package foundry.veil.imgui;

import foundry.veil.Veil;
import foundry.veil.render.pipeline.VeilRenderSystem;
import imgui.ImGui;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import org.jetbrains.annotations.ApiStatus;

import static org.lwjgl.glfw.GLFW.glfwGetCurrentContext;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;

/**
 * Manages the internal ImGui state.
 */
@ApiStatus.Internal
public class VeilImGuiImpl implements VeilImGui {

    private static VeilImGui instance;

    private final ImGuiImplGlfw implGlfw;
    private final ImGuiImplGl3 implGl3;

    private VeilImGuiImpl(long window) {
        this.implGlfw = new ImGuiImplGlfw();
        this.implGl3 = new ImGuiImplGl3();

        ImGui.createContext();
        ImPlot.createContext();
        this.implGlfw.init(window, true);
        this.implGl3.init("#version 410 core");
    }

    @Override
    public void begin() {
        this.implGlfw.newFrame();
        ImGui.newFrame();

        VeilRenderSystem.renderer().getEditorManager().render();
    }

    @Override
    public void end() {
        ImGui.render();
        this.implGl3.renderDrawData(ImGui.getDrawData());

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            final long backupWindowPtr = glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            glfwMakeContextCurrent(backupWindowPtr);
        }
    }

    @Override
    public boolean mouseButtonCallback(long window, int button, int action, int mods) {
        return ImGui.getIO().getWantCaptureMouse();
    }

    @Override
    public boolean scrollCallback(long window, double xOffset, double yOffset) {
        return ImGui.getIO().getWantCaptureMouse();
    }

    @Override
    public boolean keyCallback(long window, int key, int scancode, int action, int mods) {
        return ImGui.getIO().getWantCaptureKeyboard();
    }

    @Override
    public boolean charCallback(long window, int codepoint) {
        return ImGui.getIO().getWantCaptureKeyboard();
    }

    @Override
    public boolean shouldHideMouse() {
        return ImGui.getIO().getWantCaptureMouse();
    }

    @Override
    public void free() {
        this.implGlfw.dispose();
        this.implGl3.dispose();
        ImGui.destroyContext();
    }

    public static void init(long window) {
        instance = Veil.IMGUI ? new VeilImGuiImpl(window) : new InactiveVeilImGuiImpl();
    }

    public static VeilImGui get() {
        return instance;
    }
}
