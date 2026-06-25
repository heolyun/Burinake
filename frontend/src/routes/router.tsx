import { createBrowserRouter } from 'react-router-dom';
import { AppLayout } from '../components/layout/AppLayout';
import { DashboardPage } from '../pages/DashboardPage';
import { IssueDetailPage } from '../pages/IssueDetailPage';
import { IssueListPage } from '../pages/IssueListPage';
import { ReportListPage } from '../pages/ReportListPage';
import { SettingsPage } from '../pages/SettingsPage';
import { SnapshotUploadPage } from '../pages/SnapshotUploadPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'snapshots/upload', element: <SnapshotUploadPage /> },
      { path: 'issues', element: <IssueListPage /> },
      { path: 'issues/:issueId', element: <IssueDetailPage /> },
      { path: 'reports', element: <ReportListPage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
]);
