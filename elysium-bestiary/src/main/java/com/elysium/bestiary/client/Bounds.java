package com.elysium.bestiary.client;

/**
 * How big a body plan is, in pixels, measured rather than declared.
 *
 * <p>A modded mob whose model is not the size of its hitbox is the single most
 * common visual bug in a big pack, and it is never filed as one: it reads as
 * "I keep missing it". So no model here declares a size. The generator
 * measures each plan through its part tree at bind pose and writes the three
 * numbers into the shape class; the model hands them over in this record; and
 * the renderer squashes whatever comes out onto the hitbox the entity actually
 * has. A plan drawn at any size stands exactly as tall as the thing a player
 * swings at.
 *
 * @param wide across, in pixels
 * @param tall up
 * @param deep along
 */
public record Bounds(float wide, float tall, float deep) {
}
