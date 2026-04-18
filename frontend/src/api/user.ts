import { randomUUID } from '../utils/uuid';

const STORAGE_KEY = 'oh-my-project-user-id';

export function getUserId(): string {
  let id = localStorage.getItem(STORAGE_KEY);
  if (!id) {
    id = randomUUID();
    localStorage.setItem(STORAGE_KEY, id);
  }
  return id;
}
