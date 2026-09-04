import { afterEach, describe, expect, it, vi } from "vitest";
import { preloadImage } from "./imagePreload";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("preloadImage", () => {
  it("resolves after the image loads and decodes", async () => {
    const decode = vi.fn().mockResolvedValue(undefined);

    class SuccessfulImage {
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      decode = decode;

      set src(_url: string) {
        queueMicrotask(() => this.onload?.());
      }
    }

    vi.stubGlobal("Image", SuccessfulImage);

    await expect(preloadImage("https://example.com/original.jpg")).resolves.toBeUndefined();
    expect(decode).toHaveBeenCalledOnce();
  });

  it("rejects when the image cannot be loaded", async () => {
    class FailedImage {
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;

      set src(_url: string) {
        queueMicrotask(() => this.onerror?.());
      }
    }

    vi.stubGlobal("Image", FailedImage);

    await expect(preloadImage("https://example.com/missing.jpg")).rejects.toThrow(
      "Image preload failed.",
    );
  });

  it("rejects when the image cannot be decoded", async () => {
    const decodeError = new Error("Unsupported image format.");

    class UndecodableImage {
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      decode = vi.fn().mockRejectedValue(decodeError);

      set src(_url: string) {
        queueMicrotask(() => this.onload?.());
      }
    }

    vi.stubGlobal("Image", UndecodableImage);

    await expect(preloadImage("https://example.com/original.heic")).rejects.toBe(
      decodeError,
    );
  });
});
