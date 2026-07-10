import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from '../components/layout/AppLayout';
import { DashboardPage } from '../pages/DashboardPage';
import { FireDetectionPage } from '../pages/FireDetectionPage';
import { IssueDetailPage } from '../pages/IssueDetailPage';
import { IssueListPage } from '../pages/IssueListPage';
import { ReportListPage } from '../pages/ReportListPage';
import { SettingsPage } from '../pages/SettingsPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'fire-detection', element: <FireDetectionPage /> },
      { path: 'snapshots/upload', element: <Navigate to="/fire-detection" replace /> },
      { path: 'issues', element: <IssueListPage /> },
      { path: 'issues/:issueId', element: <IssueDetailPage /> },
      { path: 'reports', element: <ReportListPage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
]);
