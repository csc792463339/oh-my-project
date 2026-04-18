import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ChatPage from './pages/ChatPage';
import AdminLoginPage from './pages/admin/AdminLoginPage';
import AdminLayout from './pages/admin/AdminLayout';
import AdminProjectsPage from './pages/admin/AdminProjectsPage';
import AdminSessionsPage from './pages/admin/AdminSessionsPage';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ChatPage />} />
        <Route path="/admin/login" element={<AdminLoginPage />} />
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<Navigate to="projects" replace />} />
          <Route path="projects" element={<AdminProjectsPage />} />
          <Route path="sessions" element={<AdminSessionsPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  </React.StrictMode>,
);
