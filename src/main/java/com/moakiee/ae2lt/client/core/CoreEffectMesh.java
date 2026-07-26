package com.moakiee.ae2lt.client.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

final class CoreEffectMesh {
    private CoreEffectMesh() {
    }

    static void icosahedron(PoseStack stack, VertexConsumer consumer, float radius,
                            float r, float g, float b, float alpha) {
        float golden = (1.0F + (float) Math.sqrt(5.0D)) * 0.5F;
        float scale = radius / (float) Math.sqrt(1.0F + golden * golden);
        Vertex[] vertices = {
                point(-1, golden, 0, scale), point(1, golden, 0, scale),
                point(-1, -golden, 0, scale), point(1, -golden, 0, scale),
                point(0, -1, golden, scale), point(0, 1, golden, scale),
                point(0, -1, -golden, scale), point(0, 1, -golden, scale),
                point(golden, 0, -1, scale), point(golden, 0, 1, scale),
                point(-golden, 0, -1, scale), point(-golden, 0, 1, scale)
        };
        int[][] faces = {
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
        };
        for (int[] indices : faces) {
            face(stack, consumer,
                    vertices[indices[0]], vertices[indices[1]], vertices[indices[2]],
                    r, g, b, alpha);
        }
    }

    static void annularSegment(PoseStack stack, VertexConsumer consumer,
                               float innerRadius, float outerRadius, float halfThickness,
                               float arcDegrees, int segments,
                               float r, float g, float b, float alpha) {
        double arc = Math.toRadians(arcDegrees);
        for (int segment = 0; segment < segments; segment++) {
            double angle0 = arc * segment / segments;
            double angle1 = arc * (segment + 1) / segments;

            quad(stack, consumer,
                    ringVertex(angle0, innerRadius, halfThickness, 0, 1, 0),
                    ringVertex(angle0, outerRadius, halfThickness, 0, 1, 0),
                    ringVertex(angle1, outerRadius, halfThickness, 0, 1, 0),
                    ringVertex(angle1, innerRadius, halfThickness, 0, 1, 0),
                    r, g, b, alpha);
            quad(stack, consumer,
                    ringVertex(angle1, innerRadius, -halfThickness, 0, -1, 0),
                    ringVertex(angle1, outerRadius, -halfThickness, 0, -1, 0),
                    ringVertex(angle0, outerRadius, -halfThickness, 0, -1, 0),
                    ringVertex(angle0, innerRadius, -halfThickness, 0, -1, 0),
                    r, g, b, alpha);

            float cos0 = (float) Math.cos(angle0);
            float sin0 = (float) Math.sin(angle0);
            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);
            quad(stack, consumer,
                    ringVertex(angle0, outerRadius, -halfThickness, cos0, 0, sin0),
                    ringVertex(angle1, outerRadius, -halfThickness, cos1, 0, sin1),
                    ringVertex(angle1, outerRadius, halfThickness, cos1, 0, sin1),
                    ringVertex(angle0, outerRadius, halfThickness, cos0, 0, sin0),
                    r, g, b, alpha);
            quad(stack, consumer,
                    ringVertex(angle1, innerRadius, -halfThickness, -cos1, 0, -sin1),
                    ringVertex(angle0, innerRadius, -halfThickness, -cos0, 0, -sin0),
                    ringVertex(angle0, innerRadius, halfThickness, -cos0, 0, -sin0),
                    ringVertex(angle1, innerRadius, halfThickness, -cos1, 0, -sin1),
                    r, g, b, alpha);
        }

        float endCos = (float) Math.cos(arc);
        float endSin = (float) Math.sin(arc);
        quad(stack, consumer,
                ringVertex(0, innerRadius, -halfThickness, 0, 0, -1),
                ringVertex(0, outerRadius, -halfThickness, 0, 0, -1),
                ringVertex(0, outerRadius, halfThickness, 0, 0, -1),
                ringVertex(0, innerRadius, halfThickness, 0, 0, -1),
                r, g, b, alpha);
        quad(stack, consumer,
                ringVertex(arc, outerRadius, -halfThickness, -endSin, 0, endCos),
                ringVertex(arc, innerRadius, -halfThickness, -endSin, 0, endCos),
                ringVertex(arc, innerRadius, halfThickness, -endSin, 0, endCos),
                ringVertex(arc, outerRadius, halfThickness, -endSin, 0, endCos),
                r, g, b, alpha);
    }

    static void cube(PoseStack stack, VertexConsumer consumer, float halfSize,
                     float r, float g, float b, float alpha) {
        float low = -halfSize;
        float high = halfSize;

        quad(stack, consumer,
                new Vertex(low, low, high, 0.0F, 0.0F, 1.0F),
                new Vertex(high, low, high, 0.0F, 0.0F, 1.0F),
                new Vertex(high, high, high, 0.0F, 0.0F, 1.0F),
                new Vertex(low, high, high, 0.0F, 0.0F, 1.0F),
                r, g, b, alpha);
        quad(stack, consumer,
                new Vertex(high, low, low, 0.0F, 0.0F, -1.0F),
                new Vertex(low, low, low, 0.0F, 0.0F, -1.0F),
                new Vertex(low, high, low, 0.0F, 0.0F, -1.0F),
                new Vertex(high, high, low, 0.0F, 0.0F, -1.0F),
                r, g, b, alpha);
        quad(stack, consumer,
                new Vertex(high, low, high, 1.0F, 0.0F, 0.0F),
                new Vertex(high, low, low, 1.0F, 0.0F, 0.0F),
                new Vertex(high, high, low, 1.0F, 0.0F, 0.0F),
                new Vertex(high, high, high, 1.0F, 0.0F, 0.0F),
                r, g, b, alpha);
        quad(stack, consumer,
                new Vertex(low, low, low, -1.0F, 0.0F, 0.0F),
                new Vertex(low, low, high, -1.0F, 0.0F, 0.0F),
                new Vertex(low, high, high, -1.0F, 0.0F, 0.0F),
                new Vertex(low, high, low, -1.0F, 0.0F, 0.0F),
                r, g, b, alpha);
        quad(stack, consumer,
                new Vertex(low, high, high, 0.0F, 1.0F, 0.0F),
                new Vertex(high, high, high, 0.0F, 1.0F, 0.0F),
                new Vertex(high, high, low, 0.0F, 1.0F, 0.0F),
                new Vertex(low, high, low, 0.0F, 1.0F, 0.0F),
                r, g, b, alpha);
        quad(stack, consumer,
                new Vertex(low, low, low, 0.0F, -1.0F, 0.0F),
                new Vertex(high, low, low, 0.0F, -1.0F, 0.0F),
                new Vertex(high, low, high, 0.0F, -1.0F, 0.0F),
                new Vertex(low, low, high, 0.0F, -1.0F, 0.0F),
                r, g, b, alpha);
    }

    private static Vertex point(float x, float y, float z, float scale) {
        float px = x * scale;
        float py = y * scale;
        float pz = z * scale;
        float length = (float) Math.sqrt(px * px + py * py + pz * pz);
        return new Vertex(px, py, pz, px / length, py / length, pz / length);
    }

    private static Vertex ringVertex(double angle, float radius, float y,
                                     float nx, float ny, float nz) {
        return new Vertex(
                radius * (float) Math.cos(angle),
                y,
                radius * (float) Math.sin(angle),
                nx, ny, nz);
    }

    private static void triangle(PoseStack stack, VertexConsumer consumer,
                                 Vertex a, Vertex b, Vertex c,
                                 float r, float g, float blue, float alpha) {
        vertex(stack, consumer, a, r, g, blue, alpha);
        vertex(stack, consumer, b, r, g, blue, alpha);
        vertex(stack, consumer, c, r, g, blue, alpha);
    }

    private static void quad(PoseStack stack, VertexConsumer consumer,
                             Vertex a, Vertex b, Vertex c, Vertex d,
                             float r, float g, float blue, float alpha) {
        triangle(stack, consumer, a, b, c, r, g, blue, alpha);
        triangle(stack, consumer, a, c, d, r, g, blue, alpha);
    }

    private static void face(PoseStack stack, VertexConsumer consumer,
                             Vertex a, Vertex b, Vertex c,
                             float r, float g, float blue, float alpha) {
        float abX = b.x - a.x;
        float abY = b.y - a.y;
        float abZ = b.z - a.z;
        float acX = c.x - a.x;
        float acY = c.y - a.y;
        float acZ = c.z - a.z;
        float nx = abY * acZ - abZ * acY;
        float ny = abZ * acX - abX * acZ;
        float nz = abX * acY - abY * acX;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        Vertex normalA = a.withNormal(nx / length, ny / length, nz / length);
        Vertex normalB = b.withNormal(nx / length, ny / length, nz / length);
        Vertex normalC = c.withNormal(nx / length, ny / length, nz / length);
        triangle(stack, consumer, normalA, normalB, normalC, r, g, blue, alpha);
    }

    private static void vertex(PoseStack stack, VertexConsumer consumer, Vertex vertex,
                               float r, float g, float b, float alpha) {
        var pose = stack.last();
        consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, vertex.nx, vertex.ny, vertex.nz);
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz) {
        private Vertex withNormal(float normalX, float normalY, float normalZ) {
            return new Vertex(x, y, z, normalX, normalY, normalZ);
        }
    }
}
