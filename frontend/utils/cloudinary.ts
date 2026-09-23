export const uploadToCloudinary = async (file: File): Promise<string> => {
    const cloudName = process.env.NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME || "dn_store";
    const uploadPreset = process.env.NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET || "dn_store_preset";
    
    console.log(`Iniciando upload para o Cloudinary (Cloud: ${cloudName}, Preset: ${uploadPreset})`);
    
    const formData = new FormData();
    formData.append("file", file);
    formData.append("upload_preset", uploadPreset);

    try {
        const res = await fetch(`https://api.cloudinary.com/v1_1/${cloudName}/image/upload`, {
            method: "POST",
            body: formData
        });

        if (!res.ok) {
            const errData = await res.json();
            console.error("Erro do Cloudinary:", errData);
            throw new Error(errData.error?.message || "Falha no upload da imagem");
        }
        
        const data = await res.json();
        return data.secure_url;
    } catch (error) {
        console.error("Fetch error no Cloudinary:", error);
        throw error;
    }
};
