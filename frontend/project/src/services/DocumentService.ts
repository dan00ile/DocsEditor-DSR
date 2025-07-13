import { Document, DocumentVersion, User } from '../types';
import { v4 as uuidv4 } from 'uuid';

export class DocumentService {
  private static readonly STORAGE_KEY = 'collabedit_document';
  private static readonly AUTO_SAVE_INTERVAL = 3000;

  static getDocument(): Document {
    const docData = localStorage.getItem(this.STORAGE_KEY);
    if (docData) {
      const doc = JSON.parse(docData);
      doc.createdAt = new Date(doc.createdAt);
      doc.updatedAt = new Date(doc.updatedAt);
      doc.versions = doc.versions.map((v: any) => ({
        ...v,
        createdAt: new Date(v.createdAt)
      }));
      return doc;
    }

    const defaultDoc: Document = {
      id: uuidv4(),
      title: 'Untitled Document',
      content: '',
      createdAt: new Date(),
      updatedAt: new Date(),
      versions: []
    };

    this.saveDocument(defaultDoc);
    return defaultDoc;
  }

  static saveDocument(document: Document): void {
    document.updatedAt = new Date();
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(document));
  }

  static createVersion(document: Document, versionName: string, user: User): DocumentVersion {
    const version: DocumentVersion = {
      id: uuidv4(),
      versionName,
      content: document.content,
      createdBy: user.id,
      createdAt: new Date(),
      authorName: user.username
    };

    document.versions.unshift(version);
    this.saveDocument(document);
    return version;
  }

  static restoreVersion(document: Document, versionId: string): Document {
    const version = document.versions.find(v => v.id === versionId);
    if (version) {
      document.content = version.content;
      document.updatedAt = new Date();
      this.saveDocument(document);
    }
    return document;
  }

  static startAutoSave(document: Document, callback: (doc: Document) => void): () => void {
    const interval = setInterval(() => {
      this.saveDocument(document);
      callback(document);
    }, this.AUTO_SAVE_INTERVAL);

    return () => clearInterval(interval);
  }
}