package foundry.veil.impl.client.imgui;

import foundry.veil.Veil;
import imgui.glfw.ImGuiImplGlfw;

public class VeilImGuiImplGlfw extends ImGuiImplGlfw {

    @Override
    public void scrollCallback(long windowId, double xOffset, double yOffset) {
        try {
            Veil.beginImGui();
            super.scrollCallback(windowId, xOffset, yOffset);
        } finally {
            Veil.endImGui();
        }
    }

    @Override
    public void keyCallback(long windowId, int key, int scancode, int action, int mods) {
        try {
            Veil.beginImGui();
            super.keyCallback(windowId, key, scancode, action, mods);
        } finally {
            Veil.endImGui();
        }
    }

    @Override
    public void windowFocusCallback(long windowId, boolean focused) {
        try {
            Veil.beginImGui();
            super.windowFocusCallback(windowId, focused);
        } finally {
            Veil.endImGui();
        }
    }

    @Override
    public void charCallback(long windowId, int c) {
        try {
            Veil.beginImGui();
            super.charCallback(windowId, c);
        } finally {
            Veil.endImGui();
        }
    }
}
