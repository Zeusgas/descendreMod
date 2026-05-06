package fr.descendre.client.render.backend;

public final class DescendreRenderBackend {
    private static DescendreMeshRenderer renderer = CpuDescendreMeshRenderer.INSTANCE;

    private DescendreRenderBackend() {}

    public static DescendreMeshRenderer renderer() {
        return renderer;
    }

    public static void useCpuRenderer() {
        renderer = CpuDescendreMeshRenderer.INSTANCE;
    }
}