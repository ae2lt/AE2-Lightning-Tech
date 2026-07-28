package com.moakiee.ae2lt.client.core;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

final class CoreEffectMesh {
    private static final int SPHERE_LONGITUDES = 24;
    private static final int SPHERE_LATITUDES = 12;
    private static final int RING_SEGMENTS = 48;

    private CoreEffectMesh() {
    }

    static void sphere(PoseStack stack, VertexConsumer consumer, float radius,
                       float r, float g, float b, float alpha) {
        for (int latitude = 0; latitude < SPHERE_LATITUDES; latitude++) {
            double theta0 = -Math.PI * 0.5D + Math.PI * latitude / SPHERE_LATITUDES;
            double theta1 = -Math.PI * 0.5D + Math.PI * (latitude + 1) / SPHERE_LATITUDES;
            for (int longitude = 0; longitude < SPHERE_LONGITUDES; longitude++) {
                double phi0 = Math.PI * 2.0D * longitude / SPHERE_LONGITUDES;
                double phi1 = Math.PI * 2.0D * (longitude + 1) / SPHERE_LONGITUDES;

                Vertex a = sphereVertex(theta0, phi0, radius);
                Vertex b0 = sphereVertex(theta1, phi0, radius);
                Vertex c = sphereVertex(theta1, phi1, radius);
                Vertex d = sphereVertex(theta0, phi1, radius);
                triangle(stack, consumer, a, b0, c, r, g, b, alpha);
                triangle(stack, consumer, a, c, d, r, g, b, alpha);
            }
        }
    }

    static void ringBand(PoseStack stack, VertexConsumer consumer,
                         float innerRadius, float outerRadius, float halfThickness,
                         float r, float g, float b, float alpha) {
        for (int segment = 0; segment < RING_SEGMENTS; segment++) {
            double angle0 = segment / (double) RING_SEGMENTS * Math.PI * 2.0D;
            double angle1 = (segment + 1) / (double) RING_SEGMENTS * Math.PI * 2.0D;
            float cos0 = (float) Math.cos(angle0);
            float sin0 = (float) Math.sin(angle0);
            float cos1 = (float) Math.cos(angle1);
            float sin1 = (float) Math.sin(angle1);

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
    }

    static void octahedron(PoseStack stack, VertexConsumer consumer, float radius,
                           float r, float g, float b, float alpha) {
        Vertex top = new Vertex(0.0F, radius, 0.0F, 0.0F, 1.0F, 0.0F);
        Vertex bottom = new Vertex(0.0F, -radius, 0.0F, 0.0F, -1.0F, 0.0F);
        Vertex east = new Vertex(radius, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F);
        Vertex west = new Vertex(-radius, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F);
        Vertex south = new Vertex(0.0F, 0.0F, radius, 0.0F, 0.0F, 1.0F);
        Vertex north = new Vertex(0.0F, 0.0F, -radius, 0.0F, 0.0F, -1.0F);

        triangle(stack, consumer, top, south, east, r, g, b, alpha);
        triangle(stack, consumer, top, west, south, r, g, b, alpha);
        triangle(stack, consumer, top, north, west, r, g, b, alpha);
        triangle(stack, consumer, top, east, north, r, g, b, alpha);
        triangle(stack, consumer, bottom, east, south, r, g, b, alpha);
        triangle(stack, consumer, bottom, south, west, r, g, b, alpha);
        triangle(stack, consumer, bottom, west, north, r, g, b, alpha);
        triangle(stack, consumer, bottom, north, east, r, g, b, alpha);
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

    private static Vertex sphereVertex(double theta, double phi, float radius) {
        float cosTheta = (float) Math.cos(theta);
        float nx = cosTheta * (float) Math.cos(phi);
        float ny = (float) Math.sin(theta);
        float nz = cosTheta * (float) Math.sin(phi);
        return new Vertex(nx * radius, ny * radius, nz * radius, nx, ny, nz);
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

    private static void vertex(PoseStack stack, VertexConsumer consumer, Vertex vertex,
                               float r, float g, float b, float alpha) {
        var pose = stack.last();
        consumer.addVertex(pose.pose(), vertex.x, vertex.y, vertex.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, vertex.nx, vertex.ny, vertex.nz);
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz) {
    }
}
