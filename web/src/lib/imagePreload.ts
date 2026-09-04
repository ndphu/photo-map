export function preloadImage(url: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const image = new Image();

    image.onload = () => {
      image.onload = null;
      image.onerror = null;

      if (typeof image.decode !== "function") {
        resolve();
        return;
      }

      void image.decode().then(resolve, reject);
    };

    image.onerror = () => {
      image.onload = null;
      image.onerror = null;
      reject(new Error("Image preload failed."));
    };

    image.src = url;
  });
}
