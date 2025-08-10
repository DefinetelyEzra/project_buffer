import { PutObjectCommand, DeleteObjectCommand } from '@aws-sdk/client-s3';
import { s3Client } from '../config/s3'; 
import sharp from 'sharp';
import { logger } from '../utils/logger';

export class UploadService {
  private static readonly BUCKET_NAME = process.env.AWS_S3_BUCKET!;
  private static readonly MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

  static async uploadImage(file: Buffer, fileName: string, mimeType: string): Promise<string> {
    try {
      // Optimize image with Sharp
      const optimizedImage = await sharp(file)
        .resize(1920, 1080, { fit: 'inside', withoutEnlargement: true })
        .jpeg({ quality: 85 })
        .toBuffer();

      const key = `images/${Date.now()}-${fileName}`;

      const command = new PutObjectCommand({
        Bucket: this.BUCKET_NAME,
        Key: key,
        Body: optimizedImage,
        ContentType: 'image/jpeg',
        ACL: 'public-read',
      });

      await s3Client.send(command);

      const imageUrl = `https://${this.BUCKET_NAME}.s3.${process.env.AWS_REGION}.amazonaws.com/${key}`;
      logger.info(`Image uploaded successfully: ${imageUrl}`);

      return imageUrl;
    } catch (error) {
      logger.error('Image upload failed:', error);
      throw new Error('Failed to upload image');
    }
  }

  static async uploadVideo(file: Buffer, fileName: string): Promise<string> {
    try {
      if (file.length > this.MAX_FILE_SIZE) {
        throw new Error('Video file too large. Maximum size is 10MB');
      }

      const key = `videos/${Date.now()}-${fileName}`;

      const command = new PutObjectCommand({
        Bucket: this.BUCKET_NAME,
        Key: key,
        Body: file,
        ContentType: 'video/mp4',
        ACL: 'public-read',
      });

      await s3Client.send(command);

      const videoUrl = `https://${this.BUCKET_NAME}.s3.${process.env.AWS_REGION}.amazonaws.com/${key}`;
      logger.info(`Video uploaded successfully: ${videoUrl}`);

      return videoUrl;
    } catch (error) {
      logger.error('Video upload failed:', error);
      throw new Error('Failed to upload video');
    }
  }

  static async deleteFile(fileUrl: string): Promise<void> {
    try {
      const key = fileUrl.split('.amazonaws.com/')[1];

      const command = new DeleteObjectCommand({
        Bucket: this.BUCKET_NAME,
        Key: key,
      });

      await s3Client.send(command);
      logger.info(`File deleted successfully: ${key}`);
    } catch (error) {
      logger.error('File deletion failed:', error);
      throw new Error('Failed to delete file');
    }
  }
}
