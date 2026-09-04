import { Navigate, Route, Routes } from 'react-router-dom';
import { HomePage } from './pages/HomePage';
import { CharacterPage } from './pages/CharacterPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/c/:name" element={<CharacterPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
